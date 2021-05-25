// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.server;

import org.aya.api.error.Problem;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.WithPos;
import org.aya.cli.CompilerFlags;
import org.aya.cli.SingleFileCompiler;
import org.aya.concrete.Stmt;
import org.aya.core.def.Def;
import org.aya.lsp.Log;
import org.aya.lsp.LspRange;
import org.aya.lsp.definition.RefLocator;
import org.aya.lsp.highlight.Highlighter;
import org.aya.lsp.highlight.Symbol;
import org.aya.lsp.language.HighlightResult;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.trace.Trace;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.glavo.kala.collection.Seq;
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
import java.util.stream.Stream;

public class AyaService implements WorkspaceService, TextDocumentService {
  private final LspLibraryManager libraryManager = new LspLibraryManager(MutableHashMap.of(), Buffer.of());
  private Set<URI> lastErrorReportedFiles = Collections.emptySet();

  public void registerLibrary(@NotNull Path path) {
    // TODO[kiva]: work with Library System when it is finished
    Log.i("Adding library path %s", path);
    libraryManager.modulePath.append(path);
  }

  public @NotNull HighlightResult loadFile(@NotNull String uri) {
    Log.d("Loading %s", uri);
    var reporter = new LspReporter();
    var highlighter = new Highlighter(Buffer.of(), Buffer.of());
    var trace = new Trace.Builder(highlighter);
    var compiler = new SingleFileCompiler(reporter, libraryManager, trace);
    var compilerFlags = new CompilerFlags(
      CompilerFlags.Message.EMOJI, false, null,
      libraryManager.modulePath.view());

    var filePath = Path.of(URI.create(uri));
    var symbols = Buffer.<Symbol>of();
    try {
      compiler.compile(filePath, compilerFlags,
        stmts -> stmts.forEach(s -> s.accept(highlighter, symbols)),
        (stmts, defs) -> {
          libraryManager.loadedFiles.put(uri, new AyaFile(defs, stmts));
          defs.forEach(d -> d.accept(highlighter, symbols));
          highlighter.visitCallTerms(symbols);
          highlighter.visitPatterns(symbols);
        });
    } catch (IOException e) {
      Log.e("Unable to read file %s", filePath.toAbsolutePath());
    }
    reportErrors(reporter);
    return new HighlightResult(uri, symbols.view().filter(t -> t.range() != LspRange.NONE));
  }

  public void reportErrors(@NotNull LspReporter reporter) {
    lastErrorReportedFiles.forEach(f ->
      Log.publishProblems(new PublishDiagnosticsParams(f.toString(), Collections.emptyList())));
    var diags = reporter.problems.stream()
      .filter(p -> p.sourcePos().belongsToSomeFile())
      .peek(p -> Log.d(p.describe().debugRender()))
      .flatMap(p -> Stream.concat(Stream.of(p), p.inlineHints().stream().map(t -> new InlineHintProblem(p, t))))
      .flatMap(p -> p.sourcePos().file().file().stream().map(uri -> Tuple.of(uri, p)))
      .collect(Collectors.groupingBy(
        t -> t._1,
        Collectors.mapping(t -> t._2, Seq.factory())
      ));

    for (var diag : diags.entrySet()) {
      Log.d("Found %d issues in %s", diag.getValue().size(), diag.getKey());
      var problems = diag.getValue()
        .collect(Collectors.groupingBy(Problem::sourcePos, Seq.factory()))
        .entrySet().stream()
        .map(kv -> toDiagnostic(kv.getKey(), kv.getValue()))
        .collect(Collectors.toList());
      Log.publishProblems(new PublishDiagnosticsParams(
        diag.getKey().toString(),
        problems
      ));
    }
    lastErrorReportedFiles = diags.keySet();
  }

  private @NotNull Diagnostic toDiagnostic(@NotNull SourcePos sourcePos, @NotNull Seq<Problem> problems) {
    var msgBuilder = new StringBuilder();
    var severity = DiagnosticSeverity.Hint;
    for (var p : problems) {
      msgBuilder.append(p.briefErrorMsg()).append('\n');
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
      var loadedFile = libraryManager.loadedFiles.getOrNull(params.getTextDocument().getUri());
      if (loadedFile == null) return Either.forLeft(Collections.emptyList());
      var position = params.getPosition();
      var locator = new RefLocator();
      locator.visitAll(loadedFile.concrete, new RefLocator.XY(position.getLine() + 1, position.getCharacter()));
      return Either.forRight(locator.locations.view().mapNotNull(pos -> {
        if (pos.data() instanceof DefVar<?, ?> defVar) {
          var target = defVar.concrete.sourcePos();
          Log.i("Resolved references, result: " + target);
          return LspRange.toLoc(pos.sourcePos(), target);
        } else if (pos.data() instanceof LocalVar localVar) {
          // TODO: location
          return null;
        } else return null;
      }).collect(Collectors.toList()));
    });
  }

  public static final class LspReporter implements Reporter {
    private final @NotNull Buffer<@NotNull Problem> problems = Buffer.of();

    @Override public void report(@NotNull Problem problem) {
      problems.append(problem);
    }
  }

  public static final record AyaFile(
    ImmutableSeq<Def> core,
    ImmutableSeq<Stmt> concrete
  ) {
  }

  public static final record LspLibraryManager(
    @NotNull MutableHashMap<@NotNull String, AyaFile> loadedFiles,
    @NotNull Buffer<Path> modulePath
  ) implements SourceFileLocator {
    @Override public @NotNull Path locate(@NotNull Path path) {
      // vscode needs absolute path
      return path.toAbsolutePath();
    }
  }

  public record InlineHintProblem(@NotNull Problem owner, WithPos<Doc> docWithPos) implements Problem {
    @Override public @NotNull SourcePos sourcePos() {
      return docWithPos.sourcePos();
    }

    @Override public @NotNull Doc describe() {
      return docWithPos.data();
    }

    @Override public @NotNull Severity level() {
      return owner.level();
    }

    @Override public @NotNull Doc brief() {
      return describe();
    }
  }
}
