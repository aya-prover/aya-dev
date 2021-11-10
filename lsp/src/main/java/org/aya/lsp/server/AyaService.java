// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.server;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableHashMap;
import kala.tuple.Tuple;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.BufferReporter;
import org.aya.api.error.Problem;
import org.aya.api.error.SourceFileLocator;
import org.aya.api.error.SourcePos;
import org.aya.api.util.WithPos;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.concrete.resolve.ShallowResolveInfo;
import org.aya.concrete.resolve.module.FileModuleLoader;
import org.aya.concrete.stmt.Stmt;
import org.aya.core.def.Def;
import org.aya.lsp.actions.ComputeTerm;
import org.aya.lsp.actions.GotoDefinition;
import org.aya.lsp.actions.SyntaxHighlight;
import org.aya.lsp.models.ComputeTermResult;
import org.aya.lsp.models.HighlightResult;
import org.aya.lsp.utils.Log;
import org.aya.lsp.utils.LspRange;
import org.aya.pretty.doc.Doc;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AyaService implements WorkspaceService, TextDocumentService {
  private final LspLibraryManager libraryManager = new LspLibraryManager(MutableHashMap.create(), DynamicSeq.create());
  private Set<Path> lastErrorReportedFiles = Collections.emptySet();

  public void registerLibrary(@NotNull Path path) {
    // TODO[kiva]: work with Library System when it is finished
    Log.i("Adding library path %s", path);
    libraryManager.modulePath.append(path);
  }

  public @NotNull HighlightResult loadFile(@NotNull String uri) {
    var filePath = Path.of(URI.create(uri));
    Log.d("Loading %s (vscode: %s)", filePath, uri);

    var reporter = new BufferReporter();
    var compiler = new SingleFileCompiler(reporter, libraryManager, null);
    var compilerFlags = new CompilerFlags(
      CompilerFlags.Message.EMOJI, false, null,
      libraryManager.modulePath.view());
    var symbols = DynamicSeq.<HighlightResult.Symbol>create();
    libraryManager.loadedFiles.remove(filePath);
    try {
      compiler.compile(filePath, compilerFlags, new FileModuleLoader.FileModuleLoaderCallback() {
        @Override
        public void onResolved(@NotNull Path sourcePath, @NotNull ShallowResolveInfo resolveInfo, @NotNull ImmutableSeq<Stmt> stmts) {
          // only build highlight for current file
          if (sourcePath.equals(filePath)) stmts.forEach(d -> d.accept(SyntaxHighlight.INSTANCE, symbols));
        }

        @Override
        public void onTycked(@NotNull Path sourcePath, @NotNull ImmutableSeq<Stmt> stmts, @NotNull ImmutableSeq<Def> defs) {
          // but store all compiled source
          libraryManager.loadedFiles.put(sourcePath, new AyaFile(defs, stmts));
          if (sourcePath.equals(filePath)) stmts.forEach(d -> d.accept(SyntaxHighlight.INSTANCE, symbols));
        }
      });
    } catch (IOException e) {
      Log.e("Unable to read file %s", filePath.toAbsolutePath());
    }
    reportErrors(reporter, DistillerOptions.PRETTY);
    return new HighlightResult(uri, symbols.view().filter(t -> t.range() != LspRange.NONE));
  }

  public void reportErrors(@NotNull BufferReporter reporter, @NotNull DistillerOptions options) {
    lastErrorReportedFiles.forEach(f ->
      Log.publishProblems(new PublishDiagnosticsParams(f.toUri().toString(), Collections.emptyList())));
    var diags = reporter.problems().stream()
      .filter(p -> p.sourcePos().belongsToSomeFile())
      .peek(p -> Log.d(p.describe(options).debugRender()))
      .flatMap(p -> Stream.concat(Stream.of(p), p.inlineHints(options).stream().map(t -> new InlineHintProblem(p, t))))
      .flatMap(p -> p.sourcePos().file().path().stream().map(uri -> Tuple.of(uri, p)))
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
      msgBuilder.append(p.computeBriefErrorMessage(options)).append('\n');
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
      var loadedFile = getLoadedFile(params.getTextDocument().getUri());
      if (loadedFile == null) return Either.forLeft(Collections.emptyList());
      return Either.forRight(GotoDefinition.invoke(params, loadedFile));
    });
  }

  private @Nullable AyaFile getLoadedFile(@NotNull String uri) {
    return libraryManager.loadedFiles.getOrNull(Path.of(URI.create(uri)));
  }

  public ComputeTermResult computeTerm(@NotNull ComputeTermResult.Params input, ComputeTerm.Kind type) {
    var loadedFile = getLoadedFile(input.uri);
    if (loadedFile == null) return ComputeTermResult.bad(input);
    return new ComputeTerm(loadedFile, type).invoke(input);
  }

  public record AyaFile(
    @NotNull ImmutableSeq<Def> core,
    @NotNull ImmutableSeq<Stmt> concrete
  ) {
  }

  public record LspLibraryManager(
    @NotNull MutableHashMap<@NotNull Path, AyaFile> loadedFiles,
    @NotNull DynamicSeq<Path> modulePath
  ) implements SourceFileLocator {
    @Override public @NotNull Path displayName(@NotNull Path path) {
      // vscode needs absolute path
      return path.toAbsolutePath();
    }
  }

  public record InlineHintProblem(@NotNull Problem owner, WithPos<Doc> docWithPos) implements Problem {
    @Override public @NotNull SourcePos sourcePos() {
      return docWithPos.sourcePos();
    }

    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return docWithPos.data();
    }

    @Override public @NotNull Severity level() {
      return owner.level();
    }

    @Override public @NotNull Doc brief(@NotNull DistillerOptions options) {
      return describe(DistillerOptions.PRETTY);
    }
  }
}
