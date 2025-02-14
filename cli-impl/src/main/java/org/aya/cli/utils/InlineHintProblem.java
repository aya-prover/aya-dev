// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.utils;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.pretty.doc.Doc;
import org.aya.util.PrettierOptions;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

/**
 * Wrapping a {@link Problem#inlineHints(PrettierOptions)} as problem.
 */
public record InlineHintProblem(@NotNull Problem owner, WithPos<Doc> docWithPos) implements Problem {
  public static @NotNull SeqView<Problem> from(@NotNull Problem problem, @NotNull PrettierOptions options) {
    return problem.inlineHints(options).map(h -> new InlineHintProblem(problem, h));
  }

  public static @NotNull ImmutableSeq<Problem> withInlineHints(@NotNull Problem problem, @NotNull PrettierOptions options) {
    return InlineHintProblem.from(problem, options).prepended(problem).toSeq();
  }

  @Override public @NotNull SourcePos sourcePos() {
    return docWithPos.sourcePos();
  }

  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return docWithPos.data();
  }

  @Override public @NotNull Severity level() {
    return owner.level();
  }

  @Override public @NotNull Doc brief(@NotNull PrettierOptions options) {
    return describe(options);
  }
}
