// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.pretty;

import org.aya.concrete.Expr;
import org.aya.core.pretty.TermPrettier;
import org.aya.pretty.doc.Doc;
import org.aya.util.Constants;
import org.aya.util.StringEscapeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000, kiva
 * @see TermPrettier
 */
public final class ExprPrettier implements Expr.Visitor<Boolean, Doc> {
  public static final @NotNull ExprPrettier INSTANCE = new ExprPrettier();

  private ExprPrettier() {
  }

  @Override public Doc visitRef(Expr.@NotNull RefExpr expr, Boolean nestedCall) {
    return Doc.plain(expr.resolvedVar().name());
  }

  @Override public Doc visitUnresolved(Expr.@NotNull UnresolvedExpr expr, Boolean nestedCall) {
    return Doc.plain(expr.name().joinToString(Constants.SCOPE_SEPARATOR));
  }

  @Override public Doc visitLam(Expr.@NotNull LamExpr expr, Boolean nestedCall) {
    return Doc.cat(
      Doc.styled(TermPrettier.KEYWORD, Doc.symbol("\\lam")),
      Doc.plain(" "),
      expr.param().toDoc(),
      expr.body() instanceof Expr.HoleExpr
        ? Doc.empty()
        : Doc.cat(Doc.plain(" => "), expr.body().toDoc())
    );
  }

  @Override public Doc visitPi(Expr.@NotNull PiExpr expr, Boolean nestedCall) {
    // TODO[kiva]: expr.co
    return Doc.cat(
      Doc.styled(TermPrettier.KEYWORD, Doc.symbol("\\Pi")),
      Doc.plain(" "),
      expr.param().toDoc(),
      Doc.plain(" -> "),
      expr.last().toDoc()
    );
  }

  @Override public Doc visitTelescopicSigma(Expr.@NotNull TelescopicSigmaExpr expr, Boolean nestedCall) {
    // TODO[kiva]: expr.co
    return Doc.cat(
      Doc.styled(TermPrettier.KEYWORD, Doc.symbol("\\Sig")),
      Doc.plain(" "),
      StmtPrettier.INSTANCE.visitTele(expr.params()),
      Doc.plain(" ** "),
      expr.last().toDoc()
    );
  }

  @Override public Doc visitUniv(Expr.@NotNull UnivExpr expr, Boolean nestedCall) {
    int u = expr.uLevel();
    int h = expr.hLevel();
    if (u == 0 && h == -1) return Doc.styled(TermPrettier.KEYWORD, "\\Prop");
    return Doc.styled(TermPrettier.KEYWORD, switch (h) {
      case Integer.MAX_VALUE -> "\\oo-Type" + (u == 0 ? "" : u);
      case 0 -> "\\Set" + (u == 0 ? "" : u);
      default -> "\\" + h + "-Type" + (u == 0 ? "" : u);
    });
  }

  @Override public Doc visitApp(Expr.@NotNull AppExpr expr, Boolean nestedCall) {
    return TermPrettier.INSTANCE.visitCalls(
      expr.function().toDoc(),
      expr.arguments(),
      arg -> arg.accept(this, true),
      nestedCall
    );
  }

  @Override public Doc visitHole(Expr.@NotNull HoleExpr expr, Boolean nestedCall) {
    var name = expr.name();
    var filling = expr.filling();
    if (name == null && filling == null) return Doc.symbol("{?}");
    if (name != null) return Doc.plain(name);
    return Doc.hsep(Doc.symbol("{"), filling.toDoc(), Doc.symbol("?}"));
  }

  @Override public Doc visitTup(Expr.@NotNull TupExpr expr, Boolean nestedCall) {
    return Doc.cat(Doc.symbol("("),
      Doc.join(Doc.plain(", "), expr.items().stream()
        .map(Expr::toDoc)),
      Doc.symbol(")"));
  }

  @Override public Doc visitProj(Expr.@NotNull ProjExpr expr, Boolean nestedCall) {
    return Doc.cat(expr.tup().toDoc(), Doc.plain("."), Doc.plain(String.valueOf(expr.ix())));
  }

  @Override public Doc visitNew(Expr.@NotNull NewExpr expr, Boolean aBoolean) {
    return Doc.cat(
      Doc.styled(TermPrettier.KEYWORD, "\\new "),
      expr.struct().toDoc(),
      Doc.symbol(" { "),
      Doc.hsep(expr.fields().view().map(t ->
        Doc.hsep(Doc.plain("|"), Doc.plain(t.name()),
          Doc.join(Doc.plain(" "), t.bindings().map(v -> Doc.plain(v._2.name()))),
          Doc.plain("=>"), t.body().toDoc())
      )),
      Doc.symbol(" }")
    );
  }

  @Override public Doc visitLitInt(Expr.@NotNull LitIntExpr expr, Boolean nestedCall) {
    return Doc.plain(String.valueOf(expr.integer()));
  }

  @Override public Doc visitLitString(Expr.@NotNull LitStringExpr expr, Boolean nestedCall) {
    return Doc.plain("\"" + StringEscapeUtil.escapeStringCharacters(expr.string()) + "\"");
  }
}
