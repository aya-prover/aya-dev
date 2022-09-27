// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.server;

import com.google.gson.JsonElement;
import kala.collection.Seq;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import org.aya.lsp.utils.Log;
import org.aya.lsp.utils.LspRange;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
import org.javacs.lsp.*;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Collectors;

public class AyaLanguageClient implements LanguageClient {
  protected @NotNull LanguageClient delegate;

  private AyaLanguageClient(@NotNull LanguageClient delegate) {
    this.delegate = delegate;
  }

  public static @NotNull AyaLanguageClient of(@NotNull LanguageClient client) {
    if (client instanceof AyaLanguageClient aya) return aya;
    return new AyaLanguageClient(client);
  }

  public void publishAyaProblems(
    @NotNull ImmutableMap<Path, ImmutableSeq<Problem>> problems,
    @NotNull DistillerOptions options
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

  public void clearAyaProblems(@NotNull ImmutableSeq<Path> files) {
    files.forEach(f -> publishDiagnostics(new PublishDiagnosticsParams(
      f.toUri(), Collections.emptyList())));
  }

  private static @NotNull Diagnostic toDiagnostic(@NotNull SourcePos sourcePos, @NotNull Seq<Problem> problems, @NotNull DistillerOptions options) {
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

  @Override public void publishDiagnostics(@NotNull PublishDiagnosticsParams publishDiagnosticsParams) {
    delegate.publishDiagnostics(publishDiagnosticsParams);
  }

  @Override public void showMessage(@NotNull ShowMessageParams showMessageParams) {
    delegate.showMessage(showMessageParams);
  }

  @Override public void logMessage(ShowMessageParams showMessageParams) {
    delegate.logMessage(showMessageParams);
  }

  @Override public void registerCapability(@NotNull String s, @NotNull JsonElement jsonElement) {
    delegate.registerCapability(s, jsonElement);
  }

  @Override public void customNotification(@NotNull String s, @NotNull JsonElement jsonElement) {
    delegate.customNotification(s, jsonElement);
  }
}
