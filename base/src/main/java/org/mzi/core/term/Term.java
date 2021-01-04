// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.term;

import org.glavo.kala.Tuple;
import org.glavo.kala.Tuple2;
import org.glavo.kala.Unit;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.core.term.CoreTerm;
import org.mzi.api.ref.Var;
import org.mzi.api.util.DTKind;
import org.mzi.api.util.NormalizeMode;
import org.mzi.core.Tele;
import org.mzi.core.visitor.Normalizer;
import org.mzi.core.visitor.Substituter;
import org.mzi.tyck.sort.LevelSubst;
import org.mzi.util.Decision;

/**
 * @author ice1000
 * A well-typed and terminating term.
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

  @Override default @NotNull Term normalize(@NotNull NormalizeMode mode) {
    return accept(Normalizer.INSTANCE, mode);
  }

  default Tuple2<@Nullable Term, @NotNull Buffer<@NotNull Tele>> splitTeleDT(int n) {
    return splitTeleDT(n, true);
  }

  default Tuple2<@Nullable Term, Buffer<@NotNull Tele>> splitTeleDT(int n, boolean buildTele) {
    var last = this;
    Tele tele = null;
    DTKind kind = null;
    var buf = buildTele ? Buffer.<@NotNull Tele>of() : null;
    while (n > 0) {
      if (tele == null) {
        if (!(last.normalize(NormalizeMode.WHNF) instanceof DT dt))
          return Tuple.of(null, buf);
        last = dt.last();
        kind = dt.kind();
        tele = dt.telescope();
      }
      if (buf != null) buf.append(tele);
      tele = tele.next();
      n--;
    }
    var term = tele == null ? last : new DT(kind, tele, last);
    return Tuple.of(term, buf);
  }

  default @Nullable Term dropTeleDT(int n) {
    return splitTeleDT(n, false)._1;
  }

  default @NotNull Buffer<@NotNull Tele> takeTeleDT(int n) {
    return splitTeleDT(n)._2;
  }

  default @Nullable Term dropTeleLam(int n) {
    var body = this;
    Tele tele = null;
    while (n > 0) {
      if (tele == null) {
        if (!(body.normalize(NormalizeMode.WHNF) instanceof LamTerm lam)) return null;
        body = lam.body();
        tele = lam.telescope();
      }
      tele = tele.next();
      n--;
    }
    return tele == null ? body : new LamTerm(tele, body);
  }

  interface Visitor<P, R> {
    R visitRef(@NotNull RefTerm term, P p);
    R visitLam(@NotNull LamTerm term, P p);
    R visitDT(@NotNull DT term, P p);
    R visitUniv(@NotNull UnivTerm term, P p);
    R visitApp(AppTerm.@NotNull Apply term, P p);
    R visitFnCall(AppTerm.@NotNull FnCall fnCall, P p);
    R visitTup(@NotNull TupTerm term, P p);
    R visitProj(@NotNull ProjTerm term, P p);
    R visitHole(@NotNull AppTerm.HoleApp term, P p);
  }

  interface BiVisitor<P, Q, R> {
    R visitRef(@NotNull RefTerm term, P p, Q q);
    R visitLam(@NotNull LamTerm term, P p, Q q);
    R visitDT(@NotNull DT term, P p, Q q);
    R visitUniv(@NotNull UnivTerm term, P p, Q q);
    R visitApp(AppTerm.@NotNull Apply term, P p, Q q);
    R visitFnCall(AppTerm.@NotNull FnCall fnCall, P p, Q q);
    R visitTup(@NotNull TupTerm term, P p, Q q);
    R visitProj(@NotNull ProjTerm term, P p, Q q);
    R visitHole(@NotNull AppTerm.HoleApp term, P p, Q q);
  }
}
