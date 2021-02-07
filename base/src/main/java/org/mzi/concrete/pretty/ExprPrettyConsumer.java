// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.pretty;

import org.glavo.kala.Unit;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Expr;
import org.mzi.pretty.doc.Doc;

public class ExprPrettyConsumer implements Expr.Visitor<Unit, Doc> {
  public static final ExprPrettyConsumer INSTANCE = new ExprPrettyConsumer();

  @Override
  public Doc visitRef(Expr.@NotNull RefExpr expr, Unit unit) {
    return Doc.empty();
  }

  @Override
  public Doc visitUnresolved(Expr.@NotNull UnresolvedExpr expr, Unit unit) {
    return Doc.empty();
  }

  @Override
  public Doc visitLam(Expr.@NotNull LamExpr expr, Unit unit) {
    return Doc.empty();
  }

  @Override
  public Doc visitPi(Expr.@NotNull PiExpr expr, Unit unit) {
    return Doc.empty();
  }

  @Override
  public Doc visitTelescopicLam(Expr.@NotNull TelescopicLamExpr expr, Unit unit) {
    return Doc.empty();
  }

  @Override
  public Doc visitTelescopicPi(Expr.@NotNull TelescopicPiExpr expr, Unit unit) {
    return Doc.empty();
  }

  @Override
  public Doc visitTelescopicSigma(Expr.@NotNull TelescopicSigmaExpr expr, Unit unit) {
    return Doc.empty();
  }

  @Override
  public Doc visitUniv(Expr.@NotNull UnivExpr expr, Unit unit) {
    return Doc.empty();
  }

  @Override
  public Doc visitApp(Expr.@NotNull AppExpr expr, Unit unit) {
    return Doc.empty();
  }

  @Override
  public Doc visitHole(Expr.@NotNull HoleExpr expr, Unit unit) {
    return Doc.empty();
  }

  @Override
  public Doc visitTup(Expr.@NotNull TupExpr expr, Unit unit) {
    return Doc.empty();
  }

  @Override
  public Doc visitProj(Expr.@NotNull ProjExpr expr, Unit unit) {
    return Doc.empty();
  }

  @Override
  public Doc visitTyped(Expr.@NotNull TypedExpr expr, Unit unit) {
    return Doc.empty();
  }

  @Override
  public Doc visitLitInt(Expr.@NotNull LitIntExpr expr, Unit unit) {
    return Doc.empty();
  }

  @Override
  public Doc visitLitString(Expr.@NotNull LitStringExpr expr, Unit unit) {
    return Doc.empty();
  }
}
