// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.core.sort.LevelEqnSet;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record LevelMismatchError(
  @NotNull SourcePos sourcePos,
  @NotNull LevelEqnSet.Eqn eqn
) implements Problem {
  @Override public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("Level mismatch: "),
      eqn.lhs().toDoc(),
      Doc.plain(" and "),
      eqn.rhs().toDoc());
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
