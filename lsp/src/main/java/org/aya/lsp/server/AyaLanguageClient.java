// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.server;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import org.aya.lsp.utils.Log;
import org.aya.lsp.utils.LspRange;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Collectors;

public interface AyaLanguageClient extends LanguageClient {
  static void publishAyaProblems(
    @NotNull AyaLanguageClient self,
    @NotNull ImmutableMap<Path, ImmutableSeq<Problem>> problems,
    @NotNull DistillerOptions options
  ) {
    problems.forEach((filePath, value) -> {
      Log.i("Found %d issues in %s", value.size(), filePath);
      var param = new PublishDiagnosticsParams(
        filePath.toUri().toString(),
        value
          .collect(Collectors.groupingBy(Problem::sourcePos, ImmutableSeq.factory()))
          .entrySet().stream()
          .map(kv -> toDiagnostic(kv.getKey(), kv.getValue(), options))
          .toList()
      );
      self.publishDiagnostics(param);
    });
  }

  static void clearAyaProblems(
    @NotNull AyaLanguageClient self,
    @NotNull ImmutableSeq<Path> files
  ) {
    files.forEach(f -> self.publishDiagnostics(
      new PublishDiagnosticsParams(f.toUri().toString(), Collections.emptyList())));
  }

  private static @NotNull Diagnostic toDiagnostic(@NotNull SourcePos sourcePos, @NotNull Seq<Problem> problems, @NotNull DistillerOptions options) {
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

  private static @NotNull DiagnosticSeverity severityOf(@NotNull Problem problem) {
    return switch (problem.level()) {
      case WARN -> DiagnosticSeverity.Warning;
      case ERROR -> DiagnosticSeverity.Error;
      case INFO -> DiagnosticSeverity.Information;
      case GOAL -> DiagnosticSeverity.Hint;
    };
  }
}
