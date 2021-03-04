// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.concrete.Expr;
import org.aya.core.term.AppTerm;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record HoleBadSpineWarn(
  @NotNull AppTerm.HoleApp term,
  @NotNull Expr expr
) implements TyckProblem {
  @Override public @NotNull Doc describe() {
    return Doc.plain("Can't perform pattern unification on hole with spine " + term.args() + ".");
  }

  @Override public @NotNull Problem.Severity level() {
    return Problem.Severity.WARN;
  }
}
