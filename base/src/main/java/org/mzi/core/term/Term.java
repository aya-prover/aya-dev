// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.core.term;

import org.glavo.kala.Tuple3;
import org.glavo.kala.Unit;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.mzi.api.core.term.CoreTerm;
import org.mzi.api.ref.Bind;
import org.mzi.api.ref.Var;
import org.mzi.api.util.NormalizeMode;
import org.mzi.core.visitor.Normalizer;
import org.mzi.core.visitor.Stripper;
import org.mzi.core.visitor.Substituter;
import org.mzi.generic.Arg;
import org.mzi.generic.ParamLike;
import org.mzi.ref.LocalVar;
import org.mzi.tyck.MetaContext;
import org.mzi.tyck.sort.LevelSubst;
import org.mzi.util.Decision;

/**
 * A well-typed and terminating term.
 *
 * @author ice1000
 */
public interface Term extends CoreTerm {
  <P, R> R accept(@NotNull Visitor<P, R> visitor, P p);
  <P, Q, R> R accept(@NotNull BiVisitor<P, Q, R> visitor, P p, Q q);
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

  interface Visitor<P, R> {
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
    public static boolean checkSubst(@NotNull Seq<@NotNull Param> params, @NotNull Seq<@NotNull ? extends @NotNull Arg<? extends Term>> args) {
      var obj = new Object() {
        boolean ok = true;
      };
      params.forEachIndexed((i, param) -> obj.ok = obj.ok && param.explicit() == args.get(i).explicit());
      return obj.ok;
    }
  }
}
