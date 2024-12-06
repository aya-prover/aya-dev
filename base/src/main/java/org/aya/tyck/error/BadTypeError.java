// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.error;

import kala.collection.mutable.MutableList;
import org.aya.generic.AyaDocile;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.core.term.Term;
import org.aya.tyck.TyckState;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.error.WithPos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public record BadTypeError(
  @Override @NotNull WithPos<Expr> expr,
  @NotNull Term actualType, @NotNull Doc observed,
  @NotNull Doc thing, @NotNull AyaDocile desired,
  @Override @NotNull TyckState state
) implements TyckError, Stateful, SourceNodeProblem {
  @Override public @NotNull Doc describe(@NotNull PrettierOptions options) {
    var list = MutableList.of(
      Doc.sep(Doc.english("The following"), observed, Doc.english("is not good:")),
      Doc.par(1, expr.data().toDoc(options)),
      Doc.sep(Doc.english("because the type"), thing, Doc.english("is not a"),
        Doc.cat(desired.toDoc(options), Doc.plain(",")), Doc.english("but instead:")));
    UnifyInfo.exprInfo(actualType, options, this, list);
    return Doc.vcat(list);
  }

  @Override public @NotNull Doc hint(@NotNull PrettierOptions options) {
    // TODO: memberDef
/*
    if (expr instanceof Expr.App app && app.function() instanceof Expr.Ref ref
      && ref.resolvedVar() instanceof DefVar<?, ?> defVar && defVar.core instanceof MemberDef) {
      var fix = new Expr.Proj(SourcePos.NONE, app.argument().term(),
        Either.right(new QualifiedID(SourcePos.NONE, defVar.name())));
      return Doc.sep(Doc.english("Did you mean"),
        Doc.code(fix.toDoc(options)),
        Doc.english("?"));
    }
*/
    return Doc.empty();
  }

  public static @NotNull BadTypeError
  appOnNonPi(@NotNull TyckState state, @NotNull WithPos<Expr> expr, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType, Doc.plain("application"),
      Doc.english("of what you applied"), _ -> Doc.english("Pi/Path type"), state);
  }

  public static @NotNull BadTypeError
  absOnNonPi(@NotNull TyckState state, @NotNull WithPos<Expr> expr, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType, Doc.plain("abstraction"),
      Doc.english("being expected"), _ -> Doc.english("Pi/Path type"), state);
  }

  public static @NotNull BadTypeError sigmaAcc(@NotNull TyckState state, @NotNull WithPos<Expr> expr, int ix, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType,
      Doc.sep(Doc.ordinal(ix), Doc.english("element projection")),
      Doc.english("of what you projected on"),
      _ -> Doc.english("Sigma type"),
      state);
  }

  public static @NotNull BadTypeError sigmaCon(@NotNull TyckState state, @NotNull WithPos<Expr> expr, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType,
      Doc.sep(Doc.plain("tuple introduction")),
      Doc.english("you checked it against"),
      _ -> Doc.english("Sigma type"),
      state);
  }

  public static @NotNull BadTypeError classAcc(@NotNull TyckState state, @NotNull WithPos<Expr> expr, @NotNull String fieldName, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType,
      Doc.sep(Doc.english("field access"), Doc.code(fieldName), Doc.plain("of")),
      Doc.english("of what you accessed"),
      _ -> Doc.english("class type"),
      state);
  }

  public static @NotNull BadTypeError classCon(@NotNull TyckState state, @NotNull WithPos<Expr> expr, @NotNull Term actualType) {
    return new BadTypeError(expr, actualType,
      Doc.sep(Doc.plain("instantiation")),
      Doc.english("you gave"),
      _ -> Doc.english("class type"),
      state);
  }

  public static @NotNull BadTypeError doNotLike(
    @NotNull TyckState state, @NotNull WithPos<Expr> expr,
    @NotNull Term actual, @NotNull AyaDocile need
  ) {
    return new BadTypeError(expr, actual, Doc.plain("expression"),
      Doc.plain("provided"), need, state);
  }
}
