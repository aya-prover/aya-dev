// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.server;

import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.cli.CompilerFlags;
import org.aya.cli.SingleFileCompiler;
import org.aya.core.def.Def;
import org.aya.lsp.Log;
import org.aya.lsp.LspRange;
import org.aya.lsp.highlight.Highlighter;
import org.aya.lsp.highlight.Symbol;
import org.aya.lsp.language.HighlightResult;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.tuple.Tuple;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class AyaService implements WorkspaceService, TextDocumentService {
  private final LspLibraryManager libraryManager = new LspLibraryManager(MutableHashMap.of(), Buffer.of());
  private Set<String> lastErrorReportedFiles = Collections.emptySet();

  public void registerLibrary(@NotNull Path path) {
    // TODO[kiva]: work with Library System when it is finished
    Log.i("Adding library path %s", path);
    libraryManager.modulePath.append(path);
  }

  public HighlightResult loadFile(@NotNull String uri) {
    Log.d("Loading %s", uri);
    var reporter = new LspReporter();
    var compiler = new SingleFileCompiler(reporter, libraryManager, null);
    var compilerFlags = new CompilerFlags(
      CompilerFlags.Message.EMOJI, false, null,
      libraryManager.modulePath.view());

    var filePath = Path.of(URI.create(uri));
    var symbols = Buffer.<Symbol>of();
    try {
      compiler.compile(filePath, compilerFlags,
        stmts -> Highlighter.buildResolved(symbols, stmts),
        defs -> {
          libraryManager.loadedFiles.put(uri, defs);
          Highlighter.buildTycked(symbols, defs);
        });
    } catch (IOException e) {
      Log.e("Unable to read file %s", filePath.toAbsolutePath());
    }
    reportErrors(reporter);
    return new HighlightResult(uri, symbols);
  }

  public void reportErrors(@NotNull LspReporter reporter) {
    lastErrorReportedFiles.forEach(f ->
      Log.publishErrors(new PublishDiagnosticsParams(f, Collections.emptyList())));
    var diags = reporter.problems.stream()
      .filter(p -> p.sourcePos().belongsToSomeFile())
      .peek(p -> Log.d(p.describe().debugRender()))
      .map(p -> Tuple.of(p.sourcePos().file().name(), new Diagnostic(LspRange.from(p.sourcePos()),
        p.describe().debugRender(),
        severityOf(p), "Aya")))
      .collect(Collectors.groupingBy(t -> t._1));
    for (var diag : diags.entrySet()) {
      Log.d("Found %d issues in %s", diag.getValue().size(), diag.getKey());
      Log.publishErrors(new PublishDiagnosticsParams(
        diag.getKey(),
        diag.getValue().stream().map(v -> v._2)
          .collect(Collectors.toList())
      ));
    }
    lastErrorReportedFiles = diags.keySet();
  }

  @Override public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
  }

  private DiagnosticSeverity severityOf(@NotNull Problem problem) {
    return switch (problem.level()) {
      case WARN -> DiagnosticSeverity.Warning;
      case ERROR -> DiagnosticSeverity.Error;
      case INFO -> DiagnosticSeverity.Information;
      case GOAL -> DiagnosticSeverity.Hint;
    };
  }

  @Override public void didOpen(DidOpenTextDocumentParams params) {
    Log.d("didOpen: " + params.getTextDocument().getUri());
  }

  @Override public void didChange(DidChangeTextDocumentParams params) {
    // TODO: incremental compilation?
  }

  @Override public void didClose(DidCloseTextDocumentParams params) {
    Log.d("didClose: " + params.getTextDocument().getUri());
  }

  @Override public void didSave(DidSaveTextDocumentParams params) {
    Log.d("didSave: " + params.getTextDocument().getUri());
  }

  @Override public void didChangeConfiguration(DidChangeConfigurationParams params) {
  }

  @Override
  public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
    return CompletableFuture.supplyAsync(() -> Either.forLeft(Collections.emptyList()));
  }

  @Override
  public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
    return CompletableFuture.supplyAsync(() -> Either.forLeft(Collections.emptyList()));
  }

  public static final class LspReporter implements Reporter {
    private final @NotNull Buffer<@NotNull Problem> problems = Buffer.of();

    @Override public void report(@NotNull Problem problem) {
      problems.append(problem);
    }
  }

  public static final record LspLibraryManager(
    @NotNull MutableHashMap<@NotNull String, ImmutableSeq<Def>> loadedFiles,
    @NotNull Buffer<Path> modulePath
  ) implements SourceFileLocator {
    @Override public @NotNull String locate(@NotNull Path path) {
      // vscode needs absolute path
      return path.toAbsolutePath().toString();
    }
  }
}
