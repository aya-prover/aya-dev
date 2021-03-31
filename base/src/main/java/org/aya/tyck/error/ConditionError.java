// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ConditionError(
  @NotNull SourcePos sourcePos,
  int i, int j,
  @NotNull Term lhs, @Nullable Term rhs
) implements Problem {
  @Override public @NotNull Doc describe() {
    var result = rhs != null ? Doc.hcat(
      Doc.plain("unify `"),
      lhs.toDoc(),
      Doc.plain("` and `"),
      rhs.toDoc(),
      Doc.plain("`")
    ) : Doc.plain("even reduce one of the clause(s) to check condition");
    return Doc.hcat(
      Doc.plain("The "),
      Doc.ordinal(i),
      Doc.plain(" clause matches on a constructor with condition(s). When checking the "),
      Doc.ordinal(j),
      Doc.plain(" condition, we failed to "),
      result
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
