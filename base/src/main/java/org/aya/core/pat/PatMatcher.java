// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pat;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.tuple.Unit;
import org.aya.api.util.Arg;
import org.aya.core.def.PrimDef;
import org.aya.core.term.CallTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Matches a term with a pattern.
 *
 * @author ice1000
 * @apiNote Use {@link PatMatcher#tryBuildSubstArgs(ImmutableSeq, SeqLike)} instead of instantiating the class directly.
 * @implNote The substitution built is made from parallel substitutions.
 */
public record PatMatcher(@NotNull Substituter.TermSubst subst) implements Pat.Visitor<Term, Unit> {
  public static @Nullable Substituter.TermSubst tryBuildSubstArgs(
    @NotNull ImmutableSeq<@NotNull Pat> pats,
    @NotNull SeqLike<@NotNull Arg<@NotNull Term>> terms
  ) {
    return tryBuildSubstTerms(pats, terms.view().map(Arg::term));
  }

  public static @Nullable Substituter.TermSubst tryBuildSubstTerms(
    @NotNull ImmutableSeq<@NotNull Pat> pats,
    @NotNull SeqLike<@NotNull Term> terms
  ) {
    var matchy = new PatMatcher(new Substituter.TermSubst(new MutableHashMap<>()));
    try {
      for (var pat : pats.zip(terms)) pat._1.accept(matchy, pat._2);
      return matchy.subst();
    } catch (Mismatch mismatch) {
      return null;
    }
  }

  @Override public Unit visitBind(Pat.@NotNull Bind bind, Term term) {
    subst.addDirectly(bind.as(), term);
    return Unit.unit();
  }

  @Override public Unit visitAbsurd(Pat.@NotNull Absurd absurd, Term term) {
    throw new Mismatch();
  }

  @Override public Unit visitPrim(Pat.@NotNull Prim prim, Term term) {
    var core = prim.ref().core;
    assert PrimDef.Factory.INSTANCE.leftOrRight(core);
    if (term instanceof CallTerm.Prim primCall && primCall.ref() == prim.ref()) return Unit.unit();
    throw new Mismatch();
  }

  @Override public Unit visitTuple(Pat.@NotNull Tuple tuple, Term term) {
    if (!(term instanceof IntroTerm.Tuple tup)) throw new Mismatch();
    var as = tuple.as();
    if (as != null) subst.addDirectly(as, tup);
    return visitList(tuple.pats(), tup.items());
  }

  private Unit visitList(ImmutableSeq<Pat> lpats, SeqLike<Term> terms) {
    assert lpats.sizeEquals(terms);
    lpats.zip(terms).forEach(pp -> pp._1.accept(this, pp._2));
    return Unit.unit();
  }

  @Override public Unit visitCtor(Pat.@NotNull Ctor ctor, Term term) {
    if (!(term instanceof CallTerm.Con conCall)) throw new Mismatch();
    var as = ctor.as();
    if (as != null) subst.addDirectly(as, conCall);
    if (ctor.ref() != conCall.ref()) throw new Mismatch();
    return visitList(ctor.params(), conCall.conArgs().view().map(Arg::term));
  }

  private static final class Mismatch extends RuntimeException {
  }
}
