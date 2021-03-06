// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.tuple.Unit;
import kala.value.Ref;
import org.aya.api.concrete.ConcreteExpr;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LevelGenVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.api.util.Arg;
import org.aya.api.util.WithPos;
import org.aya.concrete.desugar.BinOpParser;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.concrete.desugar.Desugarer;
import org.aya.concrete.visitor.ConcreteDistiller;
import org.aya.core.term.Term;
import org.aya.generic.Level;
import org.aya.generic.ParamLike;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * @author re-xyr
 */
public sealed interface Expr extends ConcreteExpr {
  <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);

  default <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    visitor.traceEntrance(this, p);
    var ret = doAccept(visitor, p);
    visitor.traceExit(ret, this, p);
    return ret;
  }

  @Override default @NotNull Expr desugar(@NotNull Reporter reporter) {
    return accept(new Desugarer(reporter, new BinOpSet(reporter)), Unit.unit());
  }

  @Override default @NotNull Doc toDoc() {
    return accept(ConcreteDistiller.INSTANCE, false);
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
    R visitUniv(@NotNull UnivExpr expr, P p);
    R visitApp(@NotNull AppExpr expr, P p);
    R visitHole(@NotNull HoleExpr expr, P p);
    R visitTup(@NotNull TupExpr expr, P p);
    R visitProj(@NotNull ProjExpr expr, P p);
    R visitNew(@NotNull NewExpr expr, P p);
    R visitLitInt(@NotNull LitIntExpr expr, P p);
    R visitLsuc(@NotNull LSucExpr expr, P p);
    R visitLmax(@NotNull LMaxExpr expr, P p);
    R visitLitString(@NotNull LitStringExpr expr, P p);
    R visitBinOpSeq(@NotNull BinOpSeq binOpSeq, P p);
    R visitError(@NotNull ErrorExpr error, P p);
  }

  interface BaseVisitor<P, R> extends Visitor<P, R> {
    R catchUnhandled(@NotNull Expr expr, P p);
    @Override default R visitUnresolved(@NotNull UnresolvedExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitRawUniv(@NotNull Expr.RawUnivExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitLitInt(@NotNull LitIntExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitLsuc(@NotNull LSucExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitLmax(@NotNull LMaxExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitLitString(@NotNull LitStringExpr expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitBinOpSeq(@NotNull BinOpSeq expr, P p) {
      return catchUnhandled(expr, p);
    }
    @Override default R visitError(@NotNull ErrorExpr error, P p) {
      return catchUnhandled(error, p);
    }
  }

  sealed interface WithTerm extends Expr {
    @NotNull Ref<Term> theCore();
    default @Nullable Term core() {
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

  record ErrorExpr(@NotNull SourcePos sourcePos, @NotNull Doc description) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitError(this, p);
    }
  }

  /**
   * @author ice1000
   */
  record HoleExpr(
    @NotNull SourcePos sourcePos,
    boolean explicit,
    @Nullable Expr filling
  ) implements Expr {
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
    @NotNull ImmutableSeq<@NotNull Arg<NamedArg>> arguments
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitApp(this, p);
    }
  }

  /**
   * @author AustinZhu
   */
  record NamedArg(
    @Nullable String name,
    @NotNull Expr expr
  ) implements Docile {
    @Override
    public @NotNull Doc toDoc() {
      if (name != null) {
        return Doc.braced(Doc.cat(Doc.plain(name), Doc.symbol(" => "), expr.toDoc()));
      }
      return expr.toDoc();
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
   * def `+` add (a b : Nat) => ...
   * def test => zero + zero
   * </pre>
   *
   * @param resolvedFrom who refers to resolvedVar? For example, in test,
   *                     <code>resolvedVar</code> refers to `DefVar of add` while
   *                     <code>resolvedFrom</code> stores the raw name used in user code.
   * @author ice1000
   */
  record RefExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Var resolvedVar,
    @NotNull String resolvedFrom,
    @NotNull Ref<Term> theCore
  ) implements Expr, WithTerm {
    public RefExpr(@NotNull SourcePos sourcePos, @NotNull Var resolvedVar, @NotNull String resolvedFrom) {
      this(sourcePos, resolvedVar, resolvedFrom, new Ref<>());
    }

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitRef(this, p);
    }
  }

  /**
   * @param hLevel specified homotopy level if positive
   * @param uLevel specified universe level if positive
   * @author re-xyr, ice1000
   * @see RawUnivExpr#NEEDED
   * @see RawUnivExpr#POLYMORPHIC
   * @see RawUnivExpr#INFINITY
   */
  record RawUnivExpr(
    @NotNull SourcePos sourcePos,
    int uLevel,
    int hLevel
  ) implements Expr {
    /** Must be specified but yet unspecified */
    public static final int NEEDED = -1;
    /** Can either be specified or polymorphic (must be polymorphic if the other level is needed) */
    public static final int POLYMORPHIC = -2;
    /** Specified to be infinity */
    public static final int INFINITY = -3;

    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitRawUniv(this, p);
    }
  }

  record UnivExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Level<LevelGenVar> uLevel,
    @NotNull Level<LevelGenVar> hLevel
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitUniv(this, p);
    }
  }

  /**
   * @author re-xyr
   */
  record TupExpr(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<@NotNull Expr> items
  ) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitTup(this, p);
    }
  }

  /**
   * @param resolvedIx will be modified during tycking
   * @author re-xyr
   */
  record ProjExpr(
    @NotNull SourcePos sourcePos,
    @NotNull Expr tup,
    @NotNull Either<Integer, WithPos<String>> ix,
    @NotNull Ref<@Nullable Var> resolvedIx,
    @NotNull Ref<Term> theCore
  ) implements Expr, WithTerm {
    public ProjExpr(
      @NotNull SourcePos sourcePos, @NotNull Expr tup,
      @NotNull Either<Integer, WithPos<String>> ix,
      @NotNull Ref<@Nullable Var> resolvedIx
    ) {
      this(sourcePos, tup, ix, resolvedIx, new Ref<>());
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

  record Field(
    @NotNull String name,
    @NotNull ImmutableSeq<WithPos<LocalVar>> bindings,
    @NotNull Expr body
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

  record LSucExpr(@NotNull SourcePos sourcePos, @NotNull Expr expr) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLsuc(this, p);
    }
  }

  record LMaxExpr(@NotNull SourcePos sourcePos, @NotNull ImmutableSeq<Expr> levels) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLmax(this, p);
    }
  }

  record LitStringExpr(@NotNull SourcePos sourcePos, @NotNull String string) implements Expr {
    @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitLitString(this, p);
    }
  }

  /**
   * @author kiva
   */
  record BinOpSeq(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<BinOpParser.Elem> seq
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
    @Nullable Expr type,
    boolean explicit
  ) implements ParamLike<Expr> {
    public Param(@NotNull SourcePos sourcePos, @NotNull LocalVar var, boolean explicit) {
      this(sourcePos, var, null, explicit);
    }

    public @NotNull Expr.Param mapExpr(@NotNull Function<@NotNull Expr, @Nullable Expr> mapper) {
      return new Param(sourcePos, ref, type != null ? mapper.apply(type) : null, explicit);
    }
  }
}
