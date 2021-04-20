// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.core.sort.LevelEqnSet;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record LevelSolverError(
  @NotNull SourcePos sourcePos,
  @NotNull LevelEqnSet eqn
) implements Problem {
  @Override public @NotNull Doc describe() {
    return Doc.vcat(
      Doc.plain("Cannot figure out the level, here are the equations:"),
      Doc.nest(1, Doc.vcat(eqn.eqns().view().map(LevelEqnSet.Eqn::toDoc)))
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
