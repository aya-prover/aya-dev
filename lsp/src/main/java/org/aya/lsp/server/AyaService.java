// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.server;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.tuple.Tuple;
import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.library.incremental.CompilerAdvisor;
import org.aya.cli.library.incremental.DelegateCompilerAdvisor;
import org.aya.cli.library.json.LibraryConfig;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.source.DiskLibraryOwner;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.library.source.MutableLibraryOwner;
import org.aya.cli.single.CompilerFlags;
import org.aya.generic.Constants;
import org.aya.generic.util.AyaFiles;
import org.aya.lsp.actions.*;
import org.aya.lsp.library.WsLibrary;
import org.aya.lsp.models.ComputeTermResult;
import org.aya.lsp.models.HighlightResult;
import org.aya.lsp.prim.LspPrimFactory;
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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AyaService implements WorkspaceService, TextDocumentService {
  private static final @NotNull CompilerFlags FLAGS = new CompilerFlags(CompilerFlags.Message.EMOJI, false, false, null, SeqView.empty(), null);

  private final BufferReporter reporter = new BufferReporter();
  private final @NotNull MutableList<LibraryOwner> libraries = MutableList.create();
  /**
   * When working with LSP, we need to track all previously created Primitives.
   * This is shared per library.
   */
  protected final @NotNull MutableMap<LibraryConfig, LspPrimFactory> primFactories = MutableMap.create();
  private final @NotNull CompilerAdvisor advisor;
  private @Nullable AyaLanguageClient client;

  public AyaService(@NotNull CompilerAdvisor advisor) {
    this.advisor = new CallbackAdvisor(this, advisor);
  }

  public @NotNull SeqView<LibraryOwner> libraries() {
    return libraries.view();
  }

  public void registerLibrary(@NotNull Path path) {
    Log.i("Adding library path %s", path);
    if (!tryAyaLibrary(path)) mockLibraries(path);
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
    libraries.appendAll(AyaFiles.collectAyaSourceFiles(path, 1)
      .map(WsLibrary::mock));
  }

  public void connect(@NotNull AyaLanguageClient client) {
    this.client = client;
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

  public @Nullable LibrarySource find(@NotNull Path moduleFile) {
    for (var lib : libraries) {
      var found = find(lib, moduleFile);
      if (found != null) return found;
    }
    return null;
  }

  public @Nullable LibrarySource find(@NotNull String uri) {
    var path = toPath(uri);
    return find(path);
  }

  @NotNull private Path toPath(@NotNull String uri) {
    return FileUtil.canonicalize(Path.of(URI.create(uri)));
  }

  public @NotNull ImmutableSeq<HighlightResult> reload() {
    return libraries().flatMap(this::loadLibrary).toImmutableSeq();
  }

  public @NotNull ImmutableSeq<HighlightResult> loadLibrary(@NotNull LibraryOwner owner) {
    Log.i("Loading library %s", owner.underlyingLibrary().name());
    // start compiling
    reporter.clear();
    var primFactory = primFactory(owner);
    try {
      LibraryCompiler.newCompiler(primFactory, reporter, FLAGS, advisor, owner).start();
    } catch (IOException e) {
      var s = new StringWriter();
      e.printStackTrace(new PrintWriter(s));
      Log.e("IOException occurred when running the compiler. Stack trace:\n%s", s.toString());
    }
    publishProblems(reporter, DistillerOptions.pretty());
    return SyntaxHighlight.invoke(owner);
  }

  public void publishProblems(@NotNull BufferReporter reporter, @NotNull DistillerOptions options) {
    if (client == null) return;
    var diags = reporter.problems().stream()
      .filter(p -> p.sourcePos().belongsToSomeFile())
      .peek(p -> Log.d("%s", p.describe(options).debugRender()))
      .flatMap(p -> Stream.concat(Stream.of(p), p.inlineHints(options).stream().map(t -> new InlineHintProblem(p, t))))
      .flatMap(p -> p.sourcePos().file().underlying().stream().map(uri -> Tuple.of(uri, p)))
      .collect(Collectors.groupingBy(
        t -> t._1,
        Collectors.mapping(t -> t._2, ImmutableSeq.factory())
      ));
    var from = ImmutableMap.from(diags);
    AyaLanguageClient.publishAyaProblems(client, from, options);
  }

  private void clearProblems(@NotNull ImmutableSeq<ImmutableSeq<LibrarySource>> affected) {
    if (client == null) return;
    var files = affected.flatMap(i -> i.map(LibrarySource::file));
    AyaLanguageClient.clearAyaProblems(client, files);
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
      var currentFile = Option.ofNullable(source.file());
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

  @SuppressWarnings("deprecation") @Override
  public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
    return CompletableFuture.supplyAsync(() -> {
      var source = find(params.getTextDocument().getUri());
      if (source == null) return Collections.emptyList();
      return ProjectSymbol.invoke(source)
        .map(symbol -> Either.<SymbolInformation, DocumentSymbol>forRight(symbol.document()))
        .asJava();
    });
  }

  @SuppressWarnings("deprecation") @Override
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
    return new ComputeTerm(source, type, primFactory(source.owner())).invoke(input);
  }

  private @NotNull LspPrimFactory primFactory(@NotNull LibraryOwner owner) {
    return primFactories.getOrPut(owner.underlyingLibrary(), LspPrimFactory::new);
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

  private static final class CallbackAdvisor extends DelegateCompilerAdvisor {
    private final @NotNull AyaService service;

    public CallbackAdvisor(@NotNull AyaService service, @NotNull CompilerAdvisor delegate) {
      super(delegate);
      this.service = service;
    }

    @Override
    public void notifyIncrementalJob(@NotNull ImmutableSeq<LibrarySource> modified, @NotNull ImmutableSeq<ImmutableSeq<LibrarySource>> affected) {
      super.notifyIncrementalJob(modified, affected);
      service.clearProblems(affected);
    }
  }
}
