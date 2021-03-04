// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.term;

import org.aya.api.core.term.CoreTerm;
import org.aya.api.ref.Bind;
import org.aya.api.ref.Var;
import org.aya.api.util.NormalizeMode;
import org.aya.core.pretty.TermPrettyConsumer;
import org.aya.core.visitor.Normalizer;
import org.aya.core.visitor.Stripper;
import org.aya.core.visitor.Substituter;
import org.aya.generic.Arg;
import org.aya.generic.ParamLike;
import org.aya.pretty.doc.Doc;
import org.aya.ref.LocalVar;
import org.aya.tyck.MetaContext;
import org.aya.tyck.sort.LevelSubst;
import org.aya.util.Decision;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Tuple3;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * A well-typed and terminating term.
 *
 * @author ice1000
 */
public interface Term extends CoreTerm {
  <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);
  <P, Q, R> R doAccept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q);
  default <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    visitor.traceEntrance(this, p);
    var ret = doAccept(visitor, p);
    visitor.traceExit(ret);
    return ret;
  }
  default <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q) {
    visitor.traceEntrance(this, p, q);
    var ret = doAccept(visitor, p, q);
    visitor.traceExit(ret);
    return ret;
  }
  @Contract(pure = true) @NotNull Decision whnf();

  default @NotNull Term subst(@NotNull Var var, @NotNull Term term) {
    return subst(new Substituter.TermSubst(var, term));
  }

  default @NotNull Term subst(@NotNull Substituter.TermSubst subst) {
    return subst(subst, LevelSubst.EMPTY);
  }

  default @NotNull Term subst(@NotNull Substituter.TermSubst subst, @NotNull LevelSubst levelSubst) {
    return accept(new Substituter(subst, levelSubst), Unit.INSTANCE);
  }

  default @NotNull Term strip(@NotNull MetaContext context) {
    return accept(new Stripper(context), Unit.INSTANCE);
  }

  @Override default @NotNull Term normalize(@NotNull NormalizeMode mode) {
    return accept(Normalizer.INSTANCE, mode);
  }

  default @NotNull Doc toDoc() {
    return accept(TermPrettyConsumer.INSTANCE, Unit.unit());
  }

  interface Visitor<P, R> {
    default void traceEntrance(@NotNull Term term, P p) {
    }
    default void traceExit(R r) {
    }
    R visitRef(@NotNull RefTerm term, P p);
    R visitLam(@NotNull LamTerm term, P p);
    R visitPi(@NotNull PiTerm term, P p);
    R visitSigma(@NotNull SigmaTerm term, P p);
    R visitUniv(@NotNull UnivTerm term, P p);
    R visitApp(AppTerm.@NotNull Apply term, P p);
    R visitFnCall(AppTerm.@NotNull FnCall fnCall, P p);
    R visitDataCall(AppTerm.@NotNull DataCall dataCall, P p);
    R visitTup(@NotNull TupTerm term, P p);
    R visitProj(@NotNull ProjTerm term, P p);
    R visitHole(@NotNull AppTerm.HoleApp term, P p);
  }

  interface BiVisitor<P, Q, R> {
    default void traceEntrance(@NotNull Term term, P p, Q q) {
    }
    default void traceExit(R r) {
    }
    R visitRef(@NotNull RefTerm term, P p, Q q);
    R visitLam(@NotNull LamTerm term, P p, Q q);
    R visitPi(@NotNull PiTerm term, P p, Q q);
    R visitSigma(@NotNull SigmaTerm term, P p, Q q);
    R visitUniv(@NotNull UnivTerm term, P p, Q q);
    R visitApp(AppTerm.@NotNull Apply term, P p, Q q);
    R visitFnCall(AppTerm.@NotNull FnCall fnCall, P p, Q q);
    R visitDataCall(AppTerm.@NotNull DataCall dataCall, P p, Q q);
    R visitTup(@NotNull TupTerm term, P p, Q q);
    R visitProj(@NotNull ProjTerm term, P p, Q q);
    R visitHole(@NotNull AppTerm.HoleApp term, P p, Q q);
  }

  /**
   * @author re-xyr
   */
  record Param(
    @NotNull Var ref,
    @NotNull Term type,
    boolean explicit
  ) implements Bind, ParamLike<Term> {
    public static @NotNull ImmutableSeq<@NotNull Param> fromBuffer(Buffer<Tuple3<Var, Boolean, Term>> buf) {
      return buf.toImmutableSeq().map(tup -> new Param(tup._1, tup._3, tup._2));
    }

    @Contract(" -> new") public @NotNull Arg<@NotNull Term> toArg() {
      return new Arg<>(new RefTerm(ref), explicit);
    }

    public @NotNull Term.Param subst(@NotNull Var var, @NotNull Term term) {
      return subst(new Substituter.TermSubst(var, term));
    }

    public @NotNull Term.Param subst(@NotNull Substituter.TermSubst subst) {
      return subst(subst, LevelSubst.EMPTY);
    }

    public @NotNull Term.Param subst(@NotNull Substituter.TermSubst subst, @NotNull LevelSubst levelSubst) {
      return new Param(ref, type.accept(new Substituter(subst, levelSubst), Unit.unit()), explicit);
    }

    public static @NotNull Term.Param mock(@NotNull Var hole, boolean explicit) {
      return new Param(new LocalVar("_"), new AppTerm.HoleApp(hole), explicit);
    }

    @TestOnly @Contract(pure = true)
    public static boolean checkSubst(@NotNull Seq<@NotNull Param> params, @NotNull SeqLike<@NotNull ? extends @NotNull Arg<? extends Term>> args) {
      var obj = new Object() {
        boolean ok = true;
      };
      params.forEachIndexed((i, param) -> obj.ok = obj.ok && param.explicit() == args.get(i).explicit());
      return obj.ok;
    }
  }
}
