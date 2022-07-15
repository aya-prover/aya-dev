// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.server;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import kala.tuple.Tuple;
import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.library.incremental.CompilerAdvisor;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.source.DiskLibraryOwner;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.library.source.MutableLibraryOwner;
import org.aya.cli.single.CompilerFlags;
import org.aya.core.def.PrimDef;
import org.aya.generic.Constants;
import org.aya.lsp.actions.*;
import org.aya.lsp.library.WsLibrary;
import org.aya.lsp.models.ComputeTermResult;
import org.aya.lsp.models.HighlightResult;
import org.aya.lsp.utils.Log;
import org.aya.lsp.utils.LspRange;
import org.aya.pretty.doc.Doc;
import org.aya.util.FileUtil;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.reporter.BufferReporter;
import org.aya.util.reporter.Problem;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AyaService implements WorkspaceService, TextDocumentService {
  private final BufferReporter reporter = new BufferReporter();
  private final @NotNull MutableList<LibraryOwner> libraries = MutableList.create();
  /**
   * When working with LSP, we need to track all previously created Primitives.
   * This is shared among all loaded libraries just like a Global PrimFactory before.
   *
   * @implNote consider using one shared factory among all mocked libraries, and separate factory for each real library.
   */
  protected final @NotNull PrimDef.Factory sharedPrimFactory = new PrimDef.Factory();
  private @NotNull Set<Path> lastErrorReportedFiles = Collections.emptySet();

  public void registerLibrary(@NotNull Path path) {
    Log.i("Adding library path %s", path);
    if (!tryAyaLibrary(path)) mockLibraries(path);
  }

  public @NotNull SeqView<LibraryOwner> libraries() {
    return libraries.view();
  }

  private boolean tryAyaLibrary(@Nullable Path path) {
    if (path == null) return false;
    var ayaJson = path.resolve(Constants.AYA_JSON);
    if (!Files.exists(ayaJson)) return tryAyaLibrary(path.getParent());
    try {
      var config = LibraryConfigData.fromLibraryRoot(path);
      var owner = DiskLibraryOwner.from(config);
      libraries.append(owner);
    } catch (IOException e) {
      var s = new StringWriter();
      e.printStackTrace(new PrintWriter(s));
      Log.e("Cannot load library. Stack trace:\n%s", s.toString());
    }
    // stop retrying and mocking
    return true;
  }

  private void mockLibraries(@NotNull Path path) {
    libraries.appendAll(FileUtil.collectSource(path, Constants.AYA_POSTFIX, 1)
      .map(WsLibrary::mock));
  }

  private @Nullable LibraryOwner findOwner(@Nullable Path path) {
    if (path == null) return null;
    var ayaJson = path.resolve(Constants.AYA_JSON);
    if (!Files.exists(ayaJson)) return findOwner(path.getParent());
    return libraries.find(lib -> lib.underlyingLibrary().libraryRoot().equals(path)).getOrNull();
  }

  private @Nullable LibrarySource find(@NotNull LibraryOwner owner, Path moduleFile) {
    var found = owner.librarySources().find(src -> src.file().equals(moduleFile));
    if (found.isDefined()) return found.get();
    for (var dep : owner.libraryDeps()) {
      var foundDep = find(dep, moduleFile);
      if (foundDep != null) return foundDep;
    }
    return null;
  }

  private @Nullable LibrarySource find(@NotNull Path moduleFile) {
    for (var lib : libraries) {
      var found = find(lib, moduleFile);
      if (found != null) return found;
    }
    return null;
  }

  private @Nullable LibrarySource find(@NotNull String uri) {
    var path = toPath(uri);
    return find(path);
  }

  @NotNull private Path toPath(@NotNull String uri) {
    return FileUtil.canonicalize(Path.of(URI.create(uri)));
  }

  public @NotNull List<HighlightResult> loadFile(@NotNull String uri) {
    Log.d("Loading vscode uri: %s", uri);
    var path = FileUtil.canonicalize(Path.of(URI.create(uri)));
    return loadFile(path);
  }

  public @NotNull List<HighlightResult> loadFile(@NotNull Path path) {
    if (libraries.isEmpty()) registerLibrary(path.getParent());
    // find the owner library
    var source = find(path);
    if (source == null) {
      Log.w("Cannot find source");
      return Collections.emptyList();
    }
    var owner = source.owner();
    Log.d("Found source file (%s) in library %s (root: %s): ", source.file(),
      owner.underlyingLibrary().name(), owner.underlyingLibrary().libraryRoot());
    return loadLibrary(owner);
  }

  public @NotNull List<HighlightResult> loadLibrary(@NotNull LibraryOwner owner) {
    // start compiling
    reporter.clear();
    var flags = new CompilerFlags(
      CompilerFlags.Message.EMOJI, false, true, null,
      SeqView.empty(), null);
    try {
      LibraryCompiler.newCompiler(sharedPrimFactory, reporter, flags, CompilerAdvisor.inMemory(), owner).start();
    } catch (IOException e) {
      var s = new StringWriter();
      e.printStackTrace(new PrintWriter(s));
      Log.e("IOException occurred when running the compiler. Stack trace:\n%s", s.toString());
    } finally {
      sharedPrimFactory.clear();
    }
    reportErrors(reporter, DistillerOptions.pretty());
    return SyntaxHighlight.invoke(owner);
  }

  public void reportErrors(@NotNull BufferReporter reporter, @NotNull DistillerOptions options) {
    lastErrorReportedFiles.forEach(f ->
      Log.publishProblems(new PublishDiagnosticsParams(f.toUri().toString(), Collections.emptyList())));
    var diags = reporter.problems().stream()
      .filter(p -> p.sourcePos().belongsToSomeFile())
      .peek(p -> Log.d("%s", p.describe(options).debugRender()))
      .flatMap(p -> Stream.concat(Stream.of(p), p.inlineHints(options).stream().map(t -> new InlineHintProblem(p, t))))
      .flatMap(p -> p.sourcePos().file().underlying().stream().map(uri -> Tuple.of(uri, p)))
      .collect(Collectors.groupingBy(
        t -> t._1,
        Collectors.mapping(t -> t._2, Seq.factory())
      ));

    for (var diag : diags.entrySet()) {
      var filePath = diag.getKey();
      Log.d("Found %d issues in %s", diag.getValue().size(), filePath);
      var problems = diag.getValue()
        .collect(Collectors.groupingBy(Problem::sourcePos, Seq.factory()))
        .entrySet().stream()
        .map(kv -> toDiagnostic(kv.getKey(), kv.getValue(), options))
        .collect(Collectors.toList());
      Log.publishProblems(new PublishDiagnosticsParams(
        filePath.toUri().toString(),
        problems
      ));
    }
    lastErrorReportedFiles = diags.keySet();
  }

  private @NotNull Diagnostic toDiagnostic(@NotNull SourcePos sourcePos, @NotNull Seq<Problem> problems, @NotNull DistillerOptions options) {
    var msgBuilder = new StringBuilder();
    var severity = DiagnosticSeverity.Hint;
    for (var p : problems) {
      msgBuilder.append(p.brief(options).debugRender()).append('\n');
      var ps = severityOf(p);
      if (ps.getValue() < severity.getValue()) severity = ps;
    }
    return new Diagnostic(LspRange.toRange(sourcePos),
      msgBuilder.toString(), severity, "Aya");
  }

  private DiagnosticSeverity severityOf(@NotNull Problem problem) {
    return switch (problem.level()) {
      case WARN -> DiagnosticSeverity.Warning;
      case ERROR -> DiagnosticSeverity.Error;
      case INFO -> DiagnosticSeverity.Information;
      case GOAL -> DiagnosticSeverity.Hint;
    };
  }

  @Override public void didChangeWatchedFiles(@NotNull DidChangeWatchedFilesParams params) {
    params.getChanges().forEach(change -> {
      switch (change.getType()) {
        case Created -> {
          var newSrc = toPath(change.getUri());
          switch (findOwner(newSrc)) {
            case MutableLibraryOwner ownerMut -> {
              Log.d("Created new file: %s, added to owner: %s", newSrc, ownerMut.underlyingLibrary().name());
              ownerMut.addLibrarySource(newSrc);
            }
            case null -> {
              var mock = WsLibrary.mock(newSrc);
              Log.d("Created new file: %s, mocked a library %s for it", newSrc, mock.mockConfig().name());
              libraries.append(mock);
            }
            default -> {}
          }
        }
        case Deleted -> {
          var src = find(change.getUri());
          if (src == null) return;
          Log.d("Deleted file: %s, removed from owner: %s", src.file(), src.owner().underlyingLibrary().name());
          switch (src.owner()) {
            case MutableLibraryOwner owner -> owner.removeLibrarySource(src);
            case WsLibrary owner -> libraries.removeAll(o -> o == owner);
            default -> {}
          }
        }
      }
    });
  }

  @Override public void didOpen(DidOpenTextDocumentParams params) {
  }

  @Override public void didChange(DidChangeTextDocumentParams params) {
  }

  @Override public void didClose(DidCloseTextDocumentParams params) {
  }

  @Override public void didSave(DidSaveTextDocumentParams params) {
  }

  @Override public void didChangeConfiguration(DidChangeConfigurationParams params) {
  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
    return CompletableFuture.supplyAsync(() -> Either.forLeft(Collections.emptyList()));
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
    return CompletableFuture.supplyAsync(() -> {
      var source = find(params.getTextDocument().getUri());
      if (source == null) return Either.forLeft(Collections.emptyList());
      return Either.forRight(GotoDefinition.invoke(source, params.getPosition(), libraries.view()));
    });
  }

  @Override public CompletableFuture<Hover> hover(HoverParams params) {
    return CompletableFuture.supplyAsync(() -> {
      var source = find(params.getTextDocument().getUri());
      if (source == null) return null;
      var doc = ComputeSignature.invokeHover(source, params.getPosition());
      if (doc.isEmpty()) return null;
      return new Hover(new MarkupContent(MarkupKind.PLAINTEXT, doc.debugRender()));
    });
  }

  @Override public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
    return CompletableFuture.supplyAsync(() -> {
      var source = find(params.getTextDocument().getUri());
      if (source == null) return Collections.emptyList();
      return FindReferences.invoke(source, params.getPosition(), libraries.view());
    });
  }

  @Override public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
    return CompletableFuture.supplyAsync(() -> {
      var source = find(params.getTextDocument().getUri());
      if (source == null) return null;
      var renames = Rename.rename(source, params.getPosition(), params.getNewName(), libraries.view());
      return new WorkspaceEdit(renames);
    });
  }

  @Override public CompletableFuture<Either<Range, PrepareRenameResult>> prepareRename(PrepareRenameParams params) {
    return CompletableFuture.supplyAsync(() -> {
      var source = find(params.getTextDocument().getUri());
      if (source == null) return null;
      var begin = Rename.prepare(source, params.getPosition());
      if (begin == null) return null;
      return Either.forRight(new PrepareRenameResult(LspRange.toRange(begin.sourcePos()), begin.data()));
    });
  }

  @Override
  public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(DocumentHighlightParams params) {
    return CompletableFuture.supplyAsync(() -> {
      var source = find(params.getTextDocument().getUri());
      if (source == null) return Collections.emptyList();
      var currentFile = Option.of(source.file());
      return FindReferences.findOccurrences(source, params.getPosition(), SeqView.of(source.owner()))
        // only highlight references in the current file
        .filter(pos -> pos.file().underlying().equals(currentFile))
        .map(pos -> new DocumentHighlight(LspRange.toRange(pos), DocumentHighlightKind.Read))
        .stream().toList();
    });
  }

  @Override public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
    return CompletableFuture.supplyAsync(() -> {
      var source = find(params.getTextDocument().getUri());
      if (source == null) return Collections.emptyList();
      return LensMaker.invoke(source, libraries.view());
    });
  }

  @Override public CompletableFuture<CodeLens> resolveCodeLens(CodeLens codeLens) {
    return CompletableFuture.supplyAsync(() -> LensMaker.resolve(codeLens));
  }

  @Override public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params) {
    return CompletableFuture.supplyAsync(() -> {
      var source = find(params.getTextDocument().getUri());
      if (source == null) return Collections.emptyList();
      return InlayHintMaker.invoke(source, params.getRange());
    });
  }

  @Override
  public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
    return CompletableFuture.supplyAsync(() -> {
      var source = find(params.getTextDocument().getUri());
      if (source == null) return Collections.emptyList();
      return ProjectSymbol.invoke(source)
        .map(symbol -> Either.<SymbolInformation, DocumentSymbol>forRight(symbol.document()))
        .asJava();
    });
  }

  @Override
  public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(WorkspaceSymbolParams params) {
    return CompletableFuture.supplyAsync(() -> Either.forRight(
      ProjectSymbol.invoke(libraries.view())
        .map(ProjectSymbol.Symbol::workspace)
        .asJava()));
  }

  @Override public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
    return CompletableFuture.supplyAsync(() -> {
      var source = find(params.getTextDocument().getUri());
      if (source == null) return Collections.emptyList();
      return Folding.invoke(source);
    });
  }

  public ComputeTermResult computeTerm(@NotNull ComputeTermResult.Params input, ComputeTerm.Kind type) {
    var source = find(input.uri);
    if (source == null) return ComputeTermResult.bad(input);
    return new ComputeTerm(source, type).invoke(input);
  }

  public record InlineHintProblem(@NotNull Problem owner, WithPos<Doc> docWithPos) implements Problem {
    @Override public @NotNull SourcePos sourcePos() {
      return docWithPos.sourcePos();
    }

    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return docWithPos.data();
    }

    @Override public @NotNull Severity level() {
      return Severity.INFO;
    }

    @Override public @NotNull Doc brief(@NotNull DistillerOptions options) {
      return describe(DistillerOptions.pretty());
    }
  }
}
