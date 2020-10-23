package org.mzi.concrete.term;

import asia.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.error.SourcePos;
import org.mzi.api.ref.Ref;
import org.mzi.generic.Arg;
import org.mzi.generic.DTKind;

/**
 * @author re-xyr
 */
public sealed interface Expr permits
  Expr.AppExpr,
  Expr.DTExpr,
  Expr.UnresolvedExpr,
  Expr.HoleExpr,
  Expr.LamExpr,
  Expr.RefExpr,
  Expr.UnivExpr {
  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);

  @NotNull SourcePos sourcePos();

  interface Visitor<P, R> {
    R visitRef(@NotNull RefExpr refExpr, P p);
    R visitUnresolved(@NotNull UnresolvedExpr expr, P p);
    R visitLam(@NotNull LamExpr expr, P p);
    R visitDT(@NotNull DTExpr expr, P p);
    R visitUniv(@NotNull UnivExpr expr, P p);
    R visitApp(@NotNull AppExpr expr, P p);
    R visitHole(@NotNull HoleExpr holeExpr, P p);
  }

  /**
   * @author re-xyr
   */
  record UnresolvedExpr(
    @NotNull SourcePos sourcePos,
    @NotNull String name
  ) implements Expr {
    @Override
    public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitUnresolved(this, p);
    }
  }

  /**
   * @author ice1000
   */
  record HoleExpr(
    @NotNull SourcePos sourcePos,
    @Nullable String name,
    @Nullable Expr holeExpr
  ) implements Expr {
    @Override
    public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitHole(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record AppExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr function,
    @NotNull ImmutableSeq<@NotNull Arg<Expr>> argument
  ) implements Expr {
    @Override
    public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitApp(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record DTExpr(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<@NotNull Param> binds,
    @NotNull DTKind kind
  ) implements Expr {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitDT(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record LamExpr(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<@NotNull Param> binds,
    @NotNull Expr body
  ) implements Expr {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLam(this, p);
    }
  }

  /**
   * @author ice1000
   */
  record RefExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Ref resolvedRef
  ) implements Expr {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitRef(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  // TODO: sort system - corresponding to the core syntax
  record UnivExpr(
    @NotNull SourcePos sourcePos
  ) implements Expr {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitUniv(this, p);
    }
  }
}
