// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.pretty;

import org.aya.concrete.Expr;
import org.aya.core.pretty.TermPrettier;
import org.aya.pretty.doc.Doc;
import org.aya.util.StringEscapeUtil;
import org.jetbrains.annotations.NotNull;

public class ExprPrettier implements Expr.Visitor<Boolean, Doc> {
  public static final ExprPrettier INSTANCE = new ExprPrettier();

  @Override
  public Doc visitRef(Expr.@NotNull RefExpr expr, Boolean nestedCall) {
    return Doc.plain(expr.resolvedVar().name());
  }

  @Override
  public Doc visitUnresolved(Expr.@NotNull UnresolvedExpr expr, Boolean nestedCall) {
    return Doc.plain(expr.name());
  }

  @Override
  public Doc visitLam(Expr.@NotNull LamExpr expr, Boolean nestedCall) {
    return Doc.cat(
      Doc.plain("\\lam"),
      Doc.plain(" "),
      StmtPrettier.INSTANCE.visitParam(expr.param()),
      expr.body() instanceof Expr.HoleExpr
        ? Doc.empty()
        : Doc.cat(Doc.plain(" => "), expr.body().toDoc())
    );
  }

  @Override
  public Doc visitPi(Expr.@NotNull PiExpr expr, Boolean nestedCall) {
    // TODO[kiva]: expr.co
    return Doc.cat(
      Doc.plain("\\Pi"),
      Doc.plain(" "),
      StmtPrettier.INSTANCE.visitParam(expr.param()),
      Doc.plain(" -> "),
      expr.last().toDoc()
    );
  }

  @Override
  public Doc visitTelescopicSigma(Expr.@NotNull TelescopicSigmaExpr expr, Boolean nestedCall) {
    // TODO[kiva]: expr.co
    return Doc.cat(
      Doc.plain("\\Sig"),
      Doc.plain(" "),
      StmtPrettier.INSTANCE.visitTele(expr.params()),
      Doc.plain(" ** "),
      expr.last().toDoc()
    );
  }

  @Override
  public Doc visitUniv(Expr.@NotNull UnivExpr expr, Boolean nestedCall) {
    int u = expr.uLevel();
    int h = expr.hLevel();
    if (u == 0 && h == -1) return Doc.plain("\\Prop");
    return switch (h) {
      case Integer.MAX_VALUE -> Doc.plain("\\oo-Type" + (u == 0 ? "" : u));
      case 0 -> Doc.plain("\\Set" + (u == 0 ? "" : u));
      default -> Doc.plain("\\" + h + "-Type" + (u == 0 ? "" : u));
    };
  }

  @Override
  public Doc visitApp(Expr.@NotNull AppExpr expr, Boolean nestedCall) {
    return TermPrettier.INSTANCE.visitCalls(
      expr.function().toDoc(),
      expr.arguments(),
      (arg -> arg.accept(this, true)),
      nestedCall
    );
  }

  @Override
  public Doc visitHole(Expr.@NotNull HoleExpr expr, Boolean nestedCall) {
    String name = expr.name();
    Expr filling = expr.filling();
    if (name == null && filling == null) {
      return Doc.empty();
    }
    if (name != null) {
      return Doc.plain(name);
    }
    return Doc.hsep(Doc.plain("{"), filling.toDoc(), Doc.plain("?}"));
  }

  @Override
  public Doc visitTup(Expr.@NotNull TupExpr expr, Boolean nestedCall) {
    return Doc.cat(Doc.plain("("),
      Doc.join(Doc.plain(", "), expr.items().stream()
        .map(Expr::toDoc)),
      Doc.plain(")"));
  }

  @Override
  public Doc visitProj(Expr.@NotNull ProjExpr expr, Boolean nestedCall) {
    return Doc.cat(expr.tup().toDoc(), Doc.plain("."), Doc.plain(String.valueOf(expr.ix())));
  }

  @Override
  public Doc visitNew(Expr.@NotNull NewExpr expr, Boolean aBoolean) {
    return Doc.cat(
      Doc.plain("\\new "),
      expr.struct().toDoc(),
      Doc.plain(" { "),
      expr.fields().stream().map(t ->
        Doc.hsep(Doc.plain("|"), Doc.plain(t._1), Doc.plain("=>"), t._2.toDoc())
      ).reduce(Doc.empty(), Doc::hsep),
      Doc.plain(" }")
    );
  }

  @Override
  public Doc visitLitInt(Expr.@NotNull LitIntExpr expr, Boolean nestedCall) {
    return Doc.plain(String.valueOf(expr.integer()));
  }

  @Override
  public Doc visitLitString(Expr.@NotNull LitStringExpr expr, Boolean nestedCall) {
    return Doc.plain("\"" + StringEscapeUtil.escapeStringCharacters(expr.string()) + "\"");
  }
}
