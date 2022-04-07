// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Either;
import kala.value.Ref;
import org.aya.concrete.stmt.QualifiedID;
import org.aya.concrete.visitor.ExprView;
import org.aya.core.pat.Pat;
import org.aya.distill.BaseDistiller;
import org.aya.distill.ConcreteDistiller;
import org.aya.generic.ParamLike;
import org.aya.pretty.doc.Doc;
import org.aya.ref.LocalVar;
import org.aya.ref.Var;
import org.aya.resolve.context.ModuleContext;
import org.aya.resolve.visitor.ExprResolver;
import org.aya.resolve.visitor.StmtShallowResolver;
import org.aya.tyck.ExprTycker;
import org.aya.util.binop.BinOpParser;
import org.aya.util.distill.AyaDocile;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * @author re-xyr
 */
public sealed interface Expr extends AyaDocile, SourceNode {
  <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);

  default <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    visitor.traceEntrance(this, p);
    var ret = doAccept(visitor, p);
    visitor.traceExit(ret, this, p);
    return ret;
  }

  /**
   * @see org.aya.concrete.stmt.Stmt#resolve
   * @see StmtShallowResolver
   */
  @Contract(pure = true)
  default Expr resolve(@NotNull ModuleContext context) {
    var exprResolver = new ExprResolver(ExprResolver.RESTRICTIVE);
    exprResolver.enterBody();
    return exprResolver.resolve(this, context);
  }

  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return new ConcreteDistiller(options).term(BaseDistiller.Outer.Free, this);
  }

  interface Visitor<P, R> {
    default void traceEntrance(@NotNull Expr expr, P p) {
    }
    default void traceExit(R r, @NotNull Expr expr, P p) {
    }
    R visitRef(@NotNull RefExpr expr, P p);
    R visitUnresolved(@NotNull UnresolvedExpr expr, P p);
    R visitLam(@NotNull LamExpr expr, P p);
    R visitPi(@NotNull PiExpr expr, P p);
    R visitSigma(@NotNull SigmaExpr expr, P p);
    R visitRawUniv(@NotNull RawUnivExpr expr, P p);
    R visitInterval(@NotNull IntervalExpr intervalExpr, P p);
    R visitLift(@NotNull LiftExpr expr, P p);
    R visitUniv(@NotNull UnivExpr expr, P p);
    R visitApp(@NotNull AppExpr expr, P p);
    R visitHole(@NotNull HoleExpr expr, P p);
    R visitTup(@NotNull TupExpr expr, P p);
    R visitProj(@NotNull ProjExpr expr, P p);
    R visitNew(@NotNull NewExpr expr, P p);
    R visitLitInt(@NotNull LitIntExpr expr, P p);
    R visitLitString(@NotNull LitStringExpr expr, P p);
    R visitBinOpSeq(@NotNull BinOpSeq binOpSeq, P p);
    R visitError(@NotNull ErrorExpr error, P p);
    R visitMetaPat(@NotNull MetaPat metaPat, P p);
    R visitTac(@NotNull TacExpr tactic, P p);
  }

  sealed interface WithTerm extends Expr {
    @NotNull Ref<ExprTycker.Result> theCore();
    default @Nullable ExprTycker.Result core() {
      return theCore().value;
    }
  }

  /**
   * @author re-xyr
   */
  record UnresolvedExpr(
    @NotNull SourcePos sourcePos,
    @NotNull QualifiedID name
  ) implements Expr {
    public UnresolvedExpr(@NotNull SourcePos sourcePos, @NotNull String name) {
      this(sourcePos, new QualifiedID(sourcePos, name));
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitUnresolved(this, p);
    }
  }

  record ErrorExpr(@NotNull SourcePos sourcePos, @NotNull AyaDocile description) implements Expr {
    public ErrorExpr(@NotNull SourcePos sourcePos, @NotNull Doc description) {
      this(sourcePos, options -> description);
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitError(this, p);
    }
  }

  /**
   * @author ice1000
   */
  record HoleExpr(
    @Override @NotNull SourcePos sourcePos,
    boolean explicit,
    @Nullable Expr filling,
    Ref<ImmutableSeq<LocalVar>> accessibleLocal
  ) implements Expr {
    public HoleExpr(@NotNull SourcePos sourcePos, boolean explicit, @Nullable Expr filling) {
      this(sourcePos, explicit, filling, new Ref<>());
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitHole(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record AppExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr function,
    @NotNull NamedArg argument
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitApp(this, p);
    }
  }

  static @NotNull Expr unapp(@NotNull Expr expr, @Nullable MutableList<NamedArg> args) {
    while (expr instanceof AppExpr app) {
      if (args != null) args.append(app.argument);
      expr = app.function;
    }
    if (args != null) args.reverse();
    return expr;
  }

  /**
   * @author AustinZhu
   */
  record NamedArg(boolean explicit, @Nullable String name, @NotNull Expr expr)
    implements AyaDocile, SourceNode, BinOpParser.Elem<Expr> {

    public NamedArg(boolean explicit, @NotNull Expr expr) {
      this(explicit, null, expr);
    }

    @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      var doc = name == null ? expr.toDoc(options) :
        Doc.braced(Doc.sep(Doc.plain(name), Doc.symbol("=>"), expr.toDoc(options)));
      return Doc.bracedUnless(doc, explicit);
    }

    @Override public @NotNull SourcePos sourcePos() {
      return expr.sourcePos();
    }
  }

  /**
   * @author re-xyr
   */
  record PiExpr(
    @NotNull SourcePos sourcePos,
    boolean co,
    @NotNull Expr.Param param,
    @NotNull Expr last
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitPi(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record LamExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr.Param param,
    @NotNull Expr body
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLam(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record SigmaExpr(
    @NotNull SourcePos sourcePos,
    boolean co,
    @NotNull ImmutableSeq<@NotNull Param> params
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitSigma(this, p);
    }
  }

  /**
   * <pre>
   * def infix + add (a b : Nat) => ...
   * def test => zero + zero
   * </pre>
   *
   * @author ice1000
   */
  record RefExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Var resolvedVar,
    @NotNull Ref<ExprTycker.Result> theCore
  ) implements Expr, WithTerm {
    public RefExpr(@NotNull SourcePos sourcePos, @NotNull Var resolvedVar) {
      this(sourcePos, resolvedVar, new Ref<>());
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitRef(this, p);
    }
  }

  record LiftExpr(@NotNull SourcePos sourcePos, @NotNull Expr expr, int lift) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLift(this, p);
    }
  }

  /**
   * @author re-xyr, ice1000
   */
  record RawUnivExpr(@NotNull SourcePos sourcePos) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitRawUniv(this, p);
    }
  }

  record IntervalExpr(@NotNull SourcePos sourcePos) implements Expr {
    @Override
    public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return  visitor.visitInterval(this, p);
    }
  }

  record UnivExpr(@NotNull SourcePos sourcePos, int lift) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitUniv(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record TupExpr(
    @Override @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<@NotNull Expr> items
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitTup(this, p);
    }
  }

  /**
   * @param resolvedIx will be set to the field's DefVar during resolving if this is a field access.
   * @author re-xyr
   */
  record ProjExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr tup,
    @NotNull Either<Integer, QualifiedID> ix,
    @Nullable Var resolvedIx,
    @NotNull Ref<ExprTycker.Result> theCore
  ) implements Expr, WithTerm {
    public ProjExpr(
      @NotNull SourcePos sourcePos, @NotNull Expr tup,
      @NotNull Either<Integer, QualifiedID> ix
    ) {
      this(sourcePos, tup, ix, null, new Ref<>());
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitProj(this, p);
    }
  }

  record NewExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr struct,
    @NotNull ImmutableSeq<Field> fields
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitNew(this, p);
    }
  }

  /**
   * @param resolvedField will be modified during tycking for LSP to function properly.
   */
  record Field(
    @NotNull WithPos<String> name,
    @NotNull ImmutableSeq<WithPos<LocalVar>> bindings,
    @NotNull Expr body,
    @NotNull Ref<Var> resolvedField
  ) {
  }

  /**
   * @author kiva
   */
  record LitIntExpr(@NotNull SourcePos sourcePos, int integer) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLitInt(this, p);
    }
  }

  record LitStringExpr(@NotNull SourcePos sourcePos, @NotNull String string) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLitString(this, p);
    }
  }

  record MetaPat(@NotNull SourcePos sourcePos, Pat.Meta meta) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitMetaPat(this, p);
    }
  }

  record TacExpr(@NotNull SourcePos sourcePos, @NotNull TacNode tacNode) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitTac(this, p);
    }
  }

  sealed interface TacNode extends AyaDocile {
    @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      return new ConcreteDistiller(options).tacNode(this);
    }

    record ExprTac(@NotNull SourcePos sourcePos, @NotNull Expr expr) implements TacNode {}

    record ListExprTac(@NotNull SourcePos sourcePos, @NotNull ImmutableSeq<TacNode> tacNodes) implements TacNode {}
  }


  /**
   * @author kiva
   */
  record BinOpSeq(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<NamedArg> seq
  ) implements Expr {
    @Override
    public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitBinOpSeq(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record Param(
    @NotNull SourcePos sourcePos,
    @NotNull LocalVar ref,
    @NotNull Expr type,
    boolean pattern,
    boolean explicit
  ) implements ParamLike<Expr> {
    public Param(@NotNull Param param, @NotNull Expr type) {
      this(param.sourcePos, param.ref, type, param.pattern, param.explicit);
    }

    public Param(@NotNull SourcePos sourcePos, @NotNull LocalVar var, boolean explicit) {
      this(sourcePos, var, new HoleExpr(sourcePos, false, null), false, explicit);
    }

    public @NotNull Expr.Param mapExpr(@NotNull Function<@NotNull Expr, @NotNull Expr> mapper) {
      return new Param(sourcePos, ref, mapper.apply(type), pattern, explicit);
    }
  }

  default @NotNull ExprView view() {
    return () -> this;
  }
}
