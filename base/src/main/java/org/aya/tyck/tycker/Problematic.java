// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import org.aya.generic.AyaDocile;
import org.aya.syntax.core.term.ErrorTerm;
import org.aya.syntax.core.term.Term;
import org.aya.tyck.Jdg;
import org.aya.util.reporter.Problem;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

/**
 * A Problematic is something that may have {@link Problem}
 *
 * @see #reporter()
 */
public interface Problematic {
  @NotNull Reporter reporter();

  default @NotNull Jdg fail(@NotNull AyaDocile expr, @NotNull Problem prob) {
    return fail(expr, ErrorTerm.typeOf(expr), prob);
  }

  default @NotNull Jdg fail(@NotNull AyaDocile expr, @NotNull Term type, @NotNull Problem prob) {
    reporter().report(prob);
    return new Jdg.Default(new ErrorTerm(expr), type);
  }

  default void fail(@NotNull Problem problem) {
    reporter().report(problem);
  }
}
