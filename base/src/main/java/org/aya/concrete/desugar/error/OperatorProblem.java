// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.desugar.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.api.util.Assoc;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.distill.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public final class OperatorProblem {
  public record FixityError(
    @NotNull String op1, @NotNull Assoc assoc1,
    @NotNull String op2, @NotNull Assoc assoc2,
    @Override @NotNull SourcePos sourcePos
  ) implements Problem {
    @Override
    public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("Cannot figure out computation order because"),
        Doc.styled(Style.code(), Doc.plain(op1)),
        Doc.parened(Doc.plain(assoc1.name())),
        Doc.plain("and"),
        Doc.styled(Style.code(), Doc.plain(op2)),
        Doc.parened(Doc.plain(assoc1.name())),
        reason()
      );
    }

    private @NotNull Doc reason() {
      return assoc1 == assoc2 && assoc1 == Assoc.Infix
        ? Doc.english("share the same precedence but no associativity was specified.")
        : Doc.english("share the same precedence but don't share the same associativity.");
    }

    @Override
    public @NotNull Doc hint() {
      return Doc.english("Make them both left/right-associative to resolve this problem.");
    }

    @Override
    public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }

  public record AmbiguousPredError(
    @NotNull String op1,
    @NotNull String op2,
    @Override @NotNull SourcePos sourcePos
  ) implements Problem {
    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }

    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("Ambiguous operator precedence detected between"),
        Doc.styled(Style.code(), Doc.plain(op1)),
        Doc.plain("and"),
        Doc.styled(Style.code(), Doc.plain(op2))
      );
    }

    @Override public @NotNull Doc hint() {
      return Doc.sep(Doc.plain("Use"),
        Doc.styled(CoreDistiller.KEYWORD.and().code(), Doc.plain("bind")),
        Doc.english("statement or insert parentheses to make it clear."));
    }
  }

  public record BindSelfError(@Override @NotNull SourcePos sourcePos) implements Problem {
    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }

    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.english("Self bind is not allowed");
    }
  }

  public record Cyclic(
    @NotNull ImmutableSeq<BinOpSet.BinOP> items
  ) implements Problem {
    @Override public @NotNull SourcePos sourcePos() {
      return items.view().map(BinOpSet.BinOP::firstBind)
        .max(Comparator.comparingInt(SourcePos::endLine));
    }

    @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
      return Doc.sep(
        Doc.english("Precedence circle found between"),
        Doc.commaList(items.view().map(BinOpSet.BinOP::name).toImmutableSeq()
          .sorted().view().map(Doc::plain))
      );
    }

    @Override public @NotNull Severity level() {
      return Severity.ERROR;
    }
  }
}
