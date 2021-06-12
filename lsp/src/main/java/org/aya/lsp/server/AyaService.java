// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.server;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.MutableHashMap;
import kala.tuple.Tuple;
import org.aya.api.error.CollectingReporter;
import org.aya.api.error.Problem;
import org.aya.api.error.SourceFileLocator;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.WithPos;
import org.aya.cli.CompilerFlags;
import org.aya.cli.SingleFileCompiler;
import org.aya.concrete.Stmt;
import org.aya.core.def.Tycked;
import org.aya.lsp.Log;
import org.aya.lsp.LspRange;
import org.aya.lsp.definition.RefLocator;
import org.aya.lsp.highlight.Highlighter;
import org.aya.lsp.highlight.Symbol;
import org.aya.lsp.language.HighlightResult;
import org.aya.pretty.doc.Doc;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
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
  private Set<Path> lastErrorReportedFiles = Collections.emptySet();

  public void registerLibrary(@NotNull Path path) {
    // TODO[kiva]: work with Library System when it is finished
    Log.i("Adding library path %s", path);
    libraryManager.modulePath.append(path);
  }

  public @NotNull HighlightResult loadFile(@NotNull String uri) {
    var filePath = Path.of(URI.create(uri));
    Log.d("Loading %s (vscode: %s)", filePath, uri);

    var reporter = new CollectingReporter();
    var compiler = new SingleFileCompiler(reporter, libraryManager, null);
    var compilerFlags = new CompilerFlags(
      CompilerFlags.Message.EMOJI, false, null,
      libraryManager.modulePath.view());

    var symbols = Buffer.<Symbol>of();
    try {
      compiler.compile(filePath, compilerFlags,
        stmts -> stmts.forEach(d -> d.accept(Highlighter.INSTANCE, symbols)),
        (stmts, defs) -> {
          libraryManager.loadedFiles.put(filePath, new AyaFile(defs, stmts));
          stmts.forEach(d -> d.accept(Highlighter.INSTANCE, symbols));
        });
    } catch (IOException e) {
      Log.e("Unable to read file %s", filePath.toAbsolutePath());
    }
    reportErrors(reporter);
    return new HighlightResult(uri, symbols.view().filter(t -> t.range() != LspRange.NONE));
  }

  public void reportErrors(@NotNull CollectingReporter reporter) {
    lastErrorReportedFiles.forEach(f ->
      Log.publishProblems(new PublishDiagnosticsParams(f.toUri().toString(), Collections.emptyList())));
    var diags = reporter.problems().stream()
      .filter(p -> p.sourcePos().belongsToSomeFile())
      .peek(p -> Log.d(p.describe().debugRender()))
      .flatMap(p -> Stream.concat(Stream.of(p), p.inlineHints().stream().map(t -> new InlineHintProblem(p, t))))
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
        .map(kv -> toDiagnostic(kv.getKey(), kv.getValue()))
        .collect(Collectors.toList());
      Log.publishProblems(new PublishDiagnosticsParams(
        filePath.toUri().toString(),
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
      var path = Path.of(URI.create(params.getTextDocument().getUri()));
      var loadedFile = libraryManager.loadedFiles.getOrNull(path);
      if (loadedFile == null) return Either.forLeft(Collections.emptyList());
      var position = params.getPosition();
      var locator = new RefLocator();
      locator.visitAll(loadedFile.concrete, new RefLocator.XY(position.getLine() + 1, position.getCharacter()));
      return Either.forRight(locator.locations.view().mapNotNull(pos -> {
        SourcePos target;
        if (pos.data() instanceof DefVar<?, ?> defVar) {
          target = defVar.concrete.sourcePos();
        } else if (pos.data() instanceof LocalVar localVar) {
          target = localVar.definition();
        } else return null;
        var res = LspRange.toLoc(pos.sourcePos(), target);
        if (res != null) Log.d("Resolved: %s in %s", target, res.getTargetUri());
        return res;
      }).collect(Collectors.toList()));
    });
  }

  public static final record AyaFile(
    ImmutableSeq<Tycked> core,
    ImmutableSeq<Stmt> concrete
  ) {
  }

  public static final record LspLibraryManager(
    @NotNull MutableHashMap<@NotNull Path, AyaFile> loadedFiles,
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
