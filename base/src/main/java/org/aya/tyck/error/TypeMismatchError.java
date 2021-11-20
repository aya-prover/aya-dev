package org.aya.tyck.error;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.ExprProblem;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Expr;
import org.aya.core.term.Term;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;

// Difference from BadTypeError: desired type is a term rather than Doc
// TODO: Maybe there is better ways to deal with this?
public record TypeMismatchError(
  @Override @NotNull Expr expr,
  @NotNull Term actualType, @NotNull Doc action,
  @NotNull Doc thing, @NotNull Term desired) implements ExprProblem {

  @Override public @NotNull Severity level() {
    return Severity.ERROR;
  }

  @Override public @NotNull Doc describe(@NotNull DistillerOptions options) {
    return Doc.vcat(
      Doc.sep(Doc.english("Unable to"), action, Doc.english("the expression")),
      Doc.par(1, expr.toDoc(options)),
      Doc.sep(Doc.english("because the type"), thing, Doc.english("is expected to be a: ")),
      Doc.par(1, desired.toDoc(options)),
      Doc.par(1, Doc.parened(Doc.sep(Doc.plain("Normalized:"), desired.normalize(null, NormalizeMode.NF).toDoc(options)))),
      Doc.sep(Doc.english("but currently it's:")),
      Doc.par(1, actualType.toDoc(options)),
      Doc.par(1, Doc.parened(Doc.sep(Doc.plain("Normalized:"), actualType.normalize(null, NormalizeMode.NF).toDoc(options))))
    );
  }

  public static @NotNull TypeMismatchError lamParam(@NotNull Expr lamExpr, @NotNull Term paramType,
                                                    @NotNull Term actualParamType) {
    return new TypeMismatchError(lamExpr, actualParamType,
      Doc.english("apply or construct"),
      Doc.english("of the lamda's param"),
      paramType);
  }
}
