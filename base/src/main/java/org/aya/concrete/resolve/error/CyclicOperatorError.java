// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.concrete.Stmt;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

public record CyclicOperatorError(
  @NotNull SourcePos sourcePos,
  @NotNull String op,
  @NotNull String target,
  @NotNull Stmt.BindPred already
) implements Problem {
  @Override public @NotNull Doc describe() {
    return Doc.hcat(
      Doc.plain("Cyclic operator precedence found after making `"),
      Doc.plain(op),
      Doc.plain("` "),
      Doc.plain(already.invert().toString()),
      Doc.plain(" than `"),
      Doc.plain(target),
      Doc.plain("`; Because `"),
      Doc.plain(op),
      Doc.plain("` is already "),
      Doc.plain(already.toString()),
      Doc.plain(" than `"),
      Doc.plain(target),
      Doc.plain("`")
    );
  }

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }
}
