// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.server;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import org.aya.lsp.utils.Log;
import org.aya.lsp.utils.LspRange;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.javacs.lsp.Diagnostic;
import org.javacs.lsp.DiagnosticSeverity;
import org.javacs.lsp.LanguageClient;
import org.javacs.lsp.PublishDiagnosticsParams;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Collectors;

public interface AyaLanguageClient extends LanguageClient {
  default void publishAyaProblems(
    @NotNull ImmutableMap<Path, ImmutableSeq<Problem>> problems,
    @NotNull PrettierOptions options
  ) {
    problems.forEach((filePath, value) -> {
      Log.i("Found %d issues in %s", value.size(), filePath);
      var param = new PublishDiagnosticsParams(
        filePath.toUri(),
        value
          .collect(Collectors.groupingBy(Problem::sourcePos, ImmutableSeq.factory()))
          .entrySet().stream()
          .map(kv -> toDiagnostic(kv.getKey(), kv.getValue(), options))
          .toList()
      );
      publishDiagnostics(param);
    });
  }

  default void clearAyaProblems(@NotNull ImmutableSeq<Path> files) {
    files.forEach(f -> publishDiagnostics(new PublishDiagnosticsParams(
      f.toUri(), Collections.emptyList())));
  }

  private static @NotNull Diagnostic toDiagnostic(@NotNull SourcePos sourcePos, @NotNull Seq<Problem> problems, @NotNull PrettierOptions options) {
    var msgBuilder = new StringBuilder();
    var severity = DiagnosticSeverity.Hint;
    for (var p : problems) {
      msgBuilder.append(p.brief(options).debugRender()).append(System.lineSeparator());
      var ps = severityOf(p);
      if (ps < severity) severity = ps;
    }
    return new Diagnostic(LspRange.toRange(sourcePos),
      severity, "", "Aya", msgBuilder.toString(), Collections.emptyList());
  }

  private static int severityOf(@NotNull Problem problem) {
    return switch (problem.level()) {
      case WARN -> DiagnosticSeverity.Warning;
      case ERROR -> DiagnosticSeverity.Error;
      case INFO -> DiagnosticSeverity.Information;
      case GOAL -> DiagnosticSeverity.Hint;
    };
  }
}
