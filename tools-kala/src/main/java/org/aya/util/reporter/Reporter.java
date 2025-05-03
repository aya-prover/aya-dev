// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.reporter;

import kala.collection.SeqView;
import org.aya.pretty.doc.Doc;
import org.aya.util.PrettierOptions;
import org.aya.util.error.PrettyError;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public interface Reporter {
  /**
   * Report a problem
   *
   * @param problem problem to report
   */
  void report(@NotNull Problem problem);

  default void reportAll(@NotNull SeqView<Problem> problems) {
    problems.forEach(this::report);
  }

  @ApiStatus.Internal
  default void reportString(@NotNull String s) {
    reportString(s, Problem.Severity.INFO);
  }

  @ApiStatus.Internal
  default void reportString(@NotNull String s, Problem.Severity severity) {
    reportDoc(Doc.english(s), severity);
  }

  @ApiStatus.Internal
  default void reportNest(@NotNull String text, int indent) {
    reportNest(text, indent, Problem.Severity.INFO);
  }

  @ApiStatus.Internal
  default void reportNest(@NotNull String text, int indent, Problem.Severity severity) {
    reportDoc(Doc.nest(indent, Doc.english(text)), severity);
  }

  @ApiStatus.Internal
  default void reportDoc(@NotNull Doc doc, final Problem.Severity severity) {
    report(dummyProblem(doc, severity));
  }

  static @NotNull Problem dummyProblem(@NotNull Doc doc, Problem.Severity severity) {
    return new Problem() {
      @Override public @NotNull SourcePos sourcePos() { return SourcePos.NONE; }
      @Override public @NotNull Severity level() { return severity; }
      @Override public @NotNull Doc describe(@NotNull PrettierOptions options) { return doc; }
    };
  }

  static @NotNull String errorMessage(
    @NotNull Problem problem, @NotNull PrettierOptions options,
    boolean unicode, boolean supportAnsi, int pageWidth
  ) {
    var prettyErrorConf = unicode
      ? PrettyError.FormatConfig.UNICODE
      : PrettyError.FormatConfig.CLASSIC;
    var doc = problem.sourcePos() == SourcePos.NONE
      ? problem.describe(options)
      : problem.toPrettyError(options, prettyErrorConf).toDoc();
    return supportAnsi
      ? doc.renderToTerminal(pageWidth, unicode)
      : doc.renderToString(pageWidth, unicode);
  }
}
