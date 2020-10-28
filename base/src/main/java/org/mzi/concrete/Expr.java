// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete;

import asia.kala.collection.immutable.ImmutableSeq;
import asia.kala.collection.immutable.ImmutableVector;
import asia.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.error.SourcePos;
import org.mzi.api.ref.Var;
import org.mzi.generic.Arg;
import org.mzi.generic.DTKind;
import org.mzi.core.Tele;

/**
 * @author re-xyr
 */
public sealed interface Expr {
  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);

  @NotNull SourcePos sourcePos();

  interface Visitor<P, R> {
    R visitRef(@NotNull RefExpr expr, P p);
    R visitUnresolved(@NotNull UnresolvedExpr expr, P p);
    R visitLam(@NotNull LamExpr expr, P p);
    R visitDT(@NotNull DTExpr expr, P p);
    R visitUniv(@NotNull UnivExpr expr, P p);
    R visitApp(@NotNull AppExpr expr, P p);
    R visitHole(@NotNull HoleExpr expr, P p);
    R visitTup(@NotNull TupExpr expr, P p);
    R visitProj(@NotNull ProjExpr expr, P p);
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
    @Nullable Expr filling
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
    @NotNull Buffer<Param> params,
    @NotNull Expr last,
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
    @NotNull Buffer<Param> params,
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
    @NotNull Var resolvedVar
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

  /**
   * @author re-xyr
   */
  record TupExpr(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableVector<Expr> items
  ) implements Expr {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitTup(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record ProjExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr tup,
    @NotNull int ix
  ) implements Expr {
    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitProj(this, p);
    }
  }
}
