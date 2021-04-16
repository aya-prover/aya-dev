// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.desugar.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.pretty.doc.Doc;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public final class OperatorProblem {
  public record AmbiguousPredError(
    @NotNull String op1,
    @NotNull String op2,
    @NotNull SourcePos sourcePos
  ) implements Problem {
    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }

    @Override public @NotNull Doc describe() {
      return Doc.hcat(
        Doc.plain("Ambiguous operator precedence detected between `"),
        Doc.plain(op1),
        Doc.plain("` and `"),
        Doc.plain(op2),
        Doc.plain("`")
      );
    }

    @Override public @NotNull Doc hint() {
      return Doc.plain("Use `bind` statement or insert parentheses to make it clear.");
    }
  }

  public record BindSelfError(
    @NotNull SourcePos sourcePos
  ) implements Problem {
    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }

    @Override public @NotNull Doc describe() {
      return Doc.plain("Self bind is not allowed");
    }
  }

  public record CircleError(
    @NotNull Buffer<BinOpSet.Elem> items
  ) implements Problem {
    @Override public @NotNull SourcePos sourcePos() {
      return items.view().map(BinOpSet.Elem::firstBind)
        .max(Comparator.comparingInt(SourcePos::endLine));
    }

    @Override public @NotNull Doc describe() {
      return Doc.cat(
        Doc.plain("Precedence circle found between "),
        Doc.plain(items.view().map(BinOpSet.Elem::name).sorted().joinToString(", "))
      );
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }
}
