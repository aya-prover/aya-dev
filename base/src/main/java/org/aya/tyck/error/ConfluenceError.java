// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record ConfluenceError(
  @NotNull SourcePos sourcePos,
  int i, int j,
  @NotNull Term lhs, @NotNull Term rhs
) implements Problem {
  @Override public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("The "),
      Doc.ordinal(i),
      Doc.plain(" and the "),
      Doc.ordinal(j),
      Doc.plain(" clauses are not confluent because we failed to unify `"),
      lhs.toDoc(),
      Doc.plain("` and `"),
      rhs.toDoc(),
      Doc.plain("`")
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
