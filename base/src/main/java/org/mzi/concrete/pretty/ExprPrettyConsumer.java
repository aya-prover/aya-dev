// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.concrete.pretty;

import org.glavo.kala.Unit;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Expr;
import org.mzi.pretty.doc.Doc;
import org.mzi.util.StringEscapeUtil;

public class ExprPrettyConsumer implements Expr.Visitor<Unit, Doc> {
  public static final ExprPrettyConsumer INSTANCE = new ExprPrettyConsumer();

  @Override
  public Doc visitRef(Expr.@NotNull RefExpr expr, Unit unit) {
    return Doc.plain(expr.resolvedVar().name());
  }

  @Override
  public Doc visitUnresolved(Expr.@NotNull UnresolvedExpr expr, Unit unit) {
    return Doc.plain(expr.name());
  }

  @Override
  public Doc visitLam(Expr.@NotNull LamExpr expr, Unit unit) {
    return Doc.cat(
      Doc.plain("\\lam"),
      Doc.plain(" "),
      StmtPrettyConsumer.INSTANCE.visitParam(expr.param()),
      expr.body() instanceof Expr.HoleExpr
        ? Doc.empty()
        : Doc.cat(Doc.plain(" => "), expr.body().toDoc())
    );
  }

  @Override
  public Doc visitPi(Expr.@NotNull PiExpr expr, Unit unit) {
    // TODO[kiva]: expr.co
    return Doc.cat(
      Doc.plain("\\Pi"),
      Doc.plain(" "),
      StmtPrettyConsumer.INSTANCE.visitParam(expr.param()),
      Doc.plain(" -> "),
      expr.last().toDoc()
    );
  }

  @Override
  public Doc visitTelescopicSigma(Expr.@NotNull TelescopicSigmaExpr expr, Unit unit) {
    // TODO[kiva]: expr.co
    return Doc.cat(
      Doc.plain("\\Sigma"),
      Doc.plain(" "),
      StmtPrettyConsumer.INSTANCE.visitTele(expr.params()),
      Doc.plain(" ** "),
      expr.last().toDoc()
    );
  }

  @Override
  public Doc visitUniv(Expr.@NotNull UnivExpr expr, Unit unit) {
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
  public Doc visitApp(Expr.@NotNull AppExpr expr, Unit unit) {
    return Doc.cat(
      expr.function().toDoc(),
      Doc.plain(" "),
      expr.arguments().stream()
        .map(arg -> arg.explicit()
          ? arg.term().toDoc()
          : Doc.cat(Doc.plain("{"), arg.term().toDoc(), Doc.plain("}")))
        .reduce(Doc.empty(), Doc::hsep)
    );
  }

  @Override
  public Doc visitHole(Expr.@NotNull HoleExpr expr, Unit unit) {
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
  public Doc visitTup(Expr.@NotNull TupExpr expr, Unit unit) {
    var items = expr.items().stream()
      .map(Expr::toDoc)
      .reduce(Doc.empty(), (acc, doc) -> Doc.join(Doc.plain(","), acc, doc));
    return Doc.cat(Doc.plain("("), items, Doc.plain(")"));
  }

  @Override
  public Doc visitProj(Expr.@NotNull ProjExpr expr, Unit unit) {
    return Doc.cat(expr.tup().toDoc(), Doc.plain("."), Doc.plain(String.valueOf(expr.ix())));
  }

  @Override
  public Doc visitTyped(Expr.@NotNull TypedExpr expr, Unit unit) {
    return Doc.cat(expr.expr().toDoc(), Doc.plain(" : "), expr.type().toDoc());
  }

  @Override
  public Doc visitLitInt(Expr.@NotNull LitIntExpr expr, Unit unit) {
    return Doc.plain(String.valueOf(expr.integer()));
  }

  @Override
  public Doc visitLitString(Expr.@NotNull LitStringExpr expr, Unit unit) {
    return Doc.plain("\"" + StringEscapeUtil.escapeStringCharacters(expr.string()) + "\"");
  }
}
