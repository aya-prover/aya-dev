// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.server;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.mutable.DynamicSeq;
import kala.tuple.Tuple;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.BufferReporter;
import org.aya.api.error.Problem;
import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.source.DiskLibraryOwner;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.single.CompilerFlags;
import org.aya.generic.Constants;
import org.aya.lsp.actions.ComputeTerm;
import org.aya.lsp.actions.GotoDefinition;
import org.aya.lsp.actions.SyntaxHighlight;
import org.aya.lsp.library.WsLibrary;
import org.aya.lsp.models.ComputeTermResult;
import org.aya.lsp.models.HighlightResult;
import org.aya.lsp.utils.Log;
import org.aya.lsp.utils.LspRange;
import org.aya.pretty.doc.Doc;
import org.aya.util.FileUtil;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
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
  private final @NotNull DynamicSeq<LibraryOwner> libraries = DynamicSeq.create();
  private Set<Path> lastErrorReportedFiles = Collections.emptySet();

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
      var owner = DiskLibraryOwner.from(reporter, config);
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
    libraries.appendAll(FileUtil.collectSource(path, Constants.AYA_POSTFIX, 1).map(
      aya -> WsLibrary.mock(reporter, FileUtil.canonicalize(aya))));
  }

  private @Nullable LibrarySource find(@NotNull LibraryOwner owner, Path moduleFile) {
    var sources = owner.librarySourceFiles();
    var found = sources.find(src -> src.file().equals(moduleFile));
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
    var path = FileUtil.canonicalize(Path.of(URI.create(uri)));
    return find(path);
  }

  public @NotNull List<HighlightResult> loadFile(@NotNull String uri) {
    Log.d("Loading vscode uri: %s", uri);
    var path = FileUtil.canonicalize(Path.of(URI.create(uri)));
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

    // start compiling
    reporter.clear();
    var flags = new CompilerFlags(
      CompilerFlags.Message.EMOJI, false, true, null,
      SeqView.empty(), null);
    try {
      LibraryCompiler.newCompiler(flags, owner).start();
    } catch (IOException e) {
      var s = new StringWriter();
      e.printStackTrace(new PrintWriter(s));
      Log.e("IOException occurred when running the compiler. Stack trace:\n%s", s.toString());
    }
    reportErrors(reporter, DistillerOptions.pretty());
    // build highlight
    var symbols = DynamicSeq.<HighlightResult>create();
    highlight(owner, symbols);
    return symbols.asJava();
  }

  private void highlight(@NotNull LibraryOwner owner, @NotNull DynamicSeq<HighlightResult> result) {
    owner.librarySourceFiles().forEach(src -> result.append(highlightOne(src)));
    for (var dep : owner.libraryDeps()) highlight(dep, result);
  }

  private @NotNull HighlightResult highlightOne(@NotNull LibrarySource source) {
    var symbols = DynamicSeq.<HighlightResult.Symbol>create();
    var program = source.program().value;
    if (program != null) program.forEach(d -> d.accept(SyntaxHighlight.INSTANCE, symbols));
    return new HighlightResult(source.file().toUri().toString(), symbols.view().filter(t -> t.range() != LspRange.NONE));
  }

  public void reportErrors(@NotNull BufferReporter reporter, @NotNull DistillerOptions options) {
    lastErrorReportedFiles.forEach(f ->
      Log.publishProblems(new PublishDiagnosticsParams(f.toUri().toString(), Collections.emptyList())));
    var diags = reporter.problems().stream()
      .filter(p -> p.sourcePos().belongsToSomeFile())
      .peek(p -> Log.d(p.describe(options).debugRender()))
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
      msgBuilder.append(p.brief(options).commonRender()).append('\n');
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

  @Override public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
  }

  @Override public void didOpen(DidOpenTextDocumentParams params) {
  }

  @Override public void didChange(DidChangeTextDocumentParams params) {
    // TODO: incremental compilation?
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
      var loadedFile = find(params.getTextDocument().getUri());
      if (loadedFile == null) return Either.forLeft(Collections.emptyList());
      return Either.forRight(GotoDefinition.invoke(params, loadedFile));
    });
  }

  public ComputeTermResult computeTerm(@NotNull ComputeTermResult.Params input, ComputeTerm.Kind type) {
    var loadedFile = find(input.uri);
    if (loadedFile == null) return ComputeTermResult.bad(input);
    return new ComputeTerm(loadedFile, type).invoke(input);
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
