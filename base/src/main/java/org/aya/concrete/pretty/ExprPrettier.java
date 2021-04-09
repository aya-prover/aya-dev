// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.pretty;

import org.aya.api.util.Arg;
import org.aya.concrete.Expr;
import org.aya.core.pretty.TermPrettier;
import org.aya.pretty.doc.Doc;
import org.aya.util.Constants;
import org.aya.util.StringEscapeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Function;

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
      Doc.styled(TermPrettier.KEYWORD, Doc.symbol("\\")),
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
      Doc.styled(TermPrettier.KEYWORD, Doc.symbol("Pi")),
      Doc.plain(" "),
      expr.param().toDoc(),
      Doc.plain(" -> "),
      expr.last().toDoc()
    );
  }

  @Override public Doc visitSigma(Expr.@NotNull SigmaExpr expr, Boolean nestedCall) {
    // TODO[kiva]: expr.co
    return Doc.cat(
      Doc.styled(TermPrettier.KEYWORD, Doc.symbol("Sig")),
      Doc.plain(" "),
      StmtPrettier.INSTANCE.visitTele(expr.params().dropLast(1)),
      Doc.plain(" ** "),
      Objects.requireNonNull(expr.params().last().type()).toDoc()
    );
  }

  @Override public Doc visitUniv(Expr.@NotNull UnivExpr expr, Boolean nestedCall) {
    int u = expr.uLevel();
    int h = expr.hLevel();
    return Doc.styled(TermPrettier.KEYWORD, switch (h) {
      case -2 -> "ooType" + (u == -1 ? "" : u);
      case 2 -> "Set" + (u == -1 ? "" : u);
      case 1 -> "Prop" + (u == -1 ? "" : u);
      default -> "Type";
    });
  }

  @Override public Doc visitApp(Expr.@NotNull AppExpr expr, Boolean nestedCall) {
    return TermPrettier.INSTANCE.visitCalls(
      expr.function().toDoc(),
      expr.arguments(),
      (nest, arg) -> arg.accept(this, nest),
      nestedCall
    );
  }

  @Override public Doc visitHole(Expr.@NotNull HoleExpr expr, Boolean nestedCall) {
    if (!expr.explicit()) return Doc.symbol("_");
    var filling = expr.filling();
    if (filling == null) return Doc.symbol("{??}");
    return Doc.hsep(Doc.symbol("{?"), filling.toDoc(), Doc.symbol("?}"));
  }

  @Override public Doc visitTup(Expr.@NotNull TupExpr expr, Boolean nestedCall) {
    return Doc.cat(Doc.symbol("("),
      Doc.join(Doc.plain(", "), expr.items().stream()
        .map(Expr::toDoc)),
      Doc.symbol(")"));
  }

  @Override public Doc visitProj(Expr.@NotNull ProjExpr expr, Boolean nestedCall) {
    return Doc.cat(expr.tup().toDoc(), Doc.plain("."), Doc.plain(expr.ix().fold(
      Objects::toString, Function.identity()
    )));
  }

  @Override public Doc visitNew(Expr.@NotNull NewExpr expr, Boolean aBoolean) {
    return Doc.cat(
      Doc.styled(TermPrettier.KEYWORD, "new "),
      expr.struct().toDoc(),
      Doc.symbol(" { "),
      Doc.hsep(expr.fields().view().map(t ->
        Doc.hsep(Doc.plain("|"), Doc.plain(t.name()),
          t.bindings().isEmpty() ? Doc.empty() :
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

  @Override
  public Doc visitBinOpSeq(Expr.@NotNull BinOpSeq binOpSeq, Boolean nestedCall) {
    return TermPrettier.INSTANCE.visitCalls(
      binOpSeq.seq().first().expr().toDoc(),
      binOpSeq.seq().view().drop(1).map(e -> new Arg<>(e.expr(), e.explicit())),
      (nest, arg) -> arg.accept(this, nest),
      nestedCall
    );
  }
}
