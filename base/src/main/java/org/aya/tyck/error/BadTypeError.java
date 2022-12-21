// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.control.Either;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.core.def.FieldDef;
import org.aya.core.term.Term;
import org.aya.generic.AyaDocile;
import org.aya.generic.ExprProblem;
import org.aya.generic.util.NormalizeMode;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.tyck.tycker.TyckState;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public record BadTypeError(
  @Override @NotNull Expr expr,
  @NotNull Term actualType, @NotNull Doc action,
  @NotNull Doc thing, @NotNull AyaDocile desired,
  @NotNull TyckState state
) implements ExprProblem, TyckError {
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    return Doc.vcat(
      Doc.sep(Doc.english("Unable to"), action, Doc.english("the expression")),
      Doc.par(1, expr.toDoc(options)),
      Doc.sep(Doc.english("because the type"), thing, Doc.english("is not a"), Doc.cat(desired.toDoc(options), Doc.plain(",")), Doc.english("but instead:")),
      Doc.par(1, actualType.toDoc(options)),
      Doc.par(1, Doc.parened(Doc.sep(Doc.plain("Normalized:"), actualType.normalize(state, NormalizeMode.NF).toDoc(options))))
    );
  }

  @Override public @NotNull Doc hint(@NotNull PrettierOptions options) {
    if (expr instanceof Expr.App app && app.function() instanceof Expr.Ref ref
      && ref.resolvedVar() instanceof DefVar<?, ?> defVar && defVar.core instanceof FieldDef) {
      var fix = new Expr.Proj(SourcePos.NONE, app.argument().term(),
        Either.right(new QualifiedID(SourcePos.NONE, defVar.name())));
      return Doc.sep(Doc.english("Did you mean"),
        Doc.code(fix.toDoc(options)),
        Doc.english("?"));
    }
    return Doc.empty();
  }

  public static @NotNull BadTypeError pi(@NotNull TyckState state, @NotNull Expr expr, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType, Doc.plain("apply"),
      Doc.english("of what you applied"), options -> Doc.english("Pi type"), state);
  }

  public static @NotNull BadTypeError sigmaAcc(@NotNull TyckState state, @NotNull Expr expr, int ix, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType,
      Doc.sep(Doc.english("project the"), Doc.ordinal(ix), Doc.english("element of")),
      Doc.english("of what you projected on"),
      options -> Doc.english("Sigma type"),
      state);
  }

  public static @NotNull BadTypeError projProp(@NotNull TyckState state, @NotNull Expr expr, int ix, @NotNull Term type) {
    return new BadTypeError(expr, type,
      Doc.sep(Doc.english("project the"), Doc.ordinal(ix), Doc.english("element of")),
      Doc.english("of what you projected on"),
      options -> Doc.english("non-Prop Sigma type"),
      state);
  }

  public static @NotNull BadTypeError sigmaCon(@NotNull TyckState state, @NotNull Expr expr, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType,
      Doc.sep(Doc.plain("construct")),
      Doc.english("you checked it against"),
      options -> Doc.english("Sigma type"),
      state);
  }

  public static @NotNull BadTypeError structAcc(@NotNull TyckState state, @NotNull Expr expr, @NotNull String fieldName, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType,
      Doc.sep(Doc.english("access field"), Doc.code(Doc.plain(fieldName)), Doc.plain("of")),
      Doc.english("of what you accessed"),
      options -> Doc.english("struct type"),
      state);
  }

  public static @NotNull BadTypeError projPropStruct(@NotNull TyckState state, @NotNull Expr expr, @NotNull String fieldName, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType,
      Doc.sep(Doc.english("access field"), Doc.code(Doc.plain(fieldName)), Doc.plain("of")),
      Doc.english("of what you accessed"),
      options -> Doc.english("non-Prop struct type"),
      state);
  }

  public static @NotNull BadTypeError structCon(@NotNull TyckState state, @NotNull Expr expr, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType,
      Doc.sep(Doc.plain("construct")),
      Doc.english("you gave"),
      options -> Doc.english("struct type"),
      state);
  }

  public static @NotNull BadTypeError univ(@NotNull TyckState state, @NotNull Expr expr, @NotNull Term actual) {
    return new BadTypeError(expr, actual,
      Doc.english("make sense of"),
      Doc.english("provided"),
      options -> Doc.english("universe"),
      state);
  }

  public static @NotNull BadTypeError partTy(@NotNull TyckState state, @NotNull Expr expr, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType,
      Doc.plain("fill the shape composed by"),
      Doc.english("of the partial element"),
      options -> Doc.english("Partial type"),
      state);
  }
}
