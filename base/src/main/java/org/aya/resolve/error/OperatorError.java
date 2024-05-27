// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.error;

import kala.collection.immutable.ImmutableSeq;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.BinOpSet;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public interface OperatorError extends Problem {
  @Override default @NotNull Problem.Severity level() { return Problem.Severity.ERROR; }
  @Override default @NotNull Stage stage() { return Stage.PARSE; }

  record Fixity(
    @NotNull String op1, @NotNull Assoc assoc1,
    @NotNull String op2, @NotNull Assoc assoc2,
    @Override @NotNull SourcePos sourcePos
  ) implements OperatorError {
    @Override
    public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("Cannot figure out computation order because"),
        Doc.code(op1),
        Doc.parened(Doc.plain(assoc1.name())),
        Doc.plain("and"),
        Doc.code(op2),
        Doc.parened(Doc.plain(assoc1.name())),
        reason()
      );
    }

    private @NotNull Doc reason() {
      return assoc1 == assoc2 && assoc1 == Assoc.Infix
        ? Doc.english("share the same precedence but no associativity was specified.")
        : Doc.english("share the same precedence but don't share the same associativity.");
    }

    @Override public @NotNull Doc hint(@NotNull PrettierOptions options) {
      return Doc.english("Make them both left/right-associative to resolve this problem.");
    }
  }

  record Precedence(
    @NotNull String op1,
    @NotNull String op2,
    @Override @NotNull SourcePos sourcePos
  ) implements OperatorError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("Ambiguous operator precedence detected between"),
        Doc.code(op1),
        Doc.plain("and"),
        Doc.code(op2)
      );
    }

    @Override public @NotNull Doc hint(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.plain("Use"),
        Doc.code(Doc.styled(BasePrettier.KEYWORD, Doc.plain("tighter/looser"))),
        Doc.english("clause or insert parentheses to make it clear."));
    }
  }

  record SelfBind(@Override @NotNull SourcePos sourcePos) implements OperatorError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.english("Self bind is not allowed");
    }
  }

  record Circular(@NotNull ImmutableSeq<BinOpSet.BinOP> items) implements OperatorError {
    @Override public @NotNull SourcePos sourcePos() {
      return items.view().map(BinOpSet.BinOP::firstBind)
        .max(Comparator.comparingInt(SourcePos::endLine));
    }

    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(
        Doc.english("Circular precedence found between"),
        Doc.commaList(items.view().map(BinOpSet.BinOP::name).toImmutableSeq()
          .sorted().view().map(Doc::plain))
      );
    }
  }

  record MissingOperand(@NotNull SourcePos sourcePos, @NotNull String op) implements OperatorError {
    @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.english("There is no operand for this operator"),
        Doc.code(op));
    }
  }
}
