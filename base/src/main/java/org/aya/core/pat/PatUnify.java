// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pat;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Unit;
import org.aya.api.ref.LocalVar;
import org.aya.core.visitor.Substituter.TermSubst;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The unification of patterns. This is <strong>not</strong> pattern unification.
 *
 * @author ice1000
 * @see PatUnify#unifyPat(SeqLike, SeqLike, TermSubst, TermSubst)
 */
public record PatUnify(@NotNull TermSubst lhsSubst, @NotNull TermSubst rhsSubst) implements Pat.Visitor<Pat, Unit> {
  @Override public Unit visitBind(Pat.@NotNull Bind bind, Pat pat) {
    return Unit.unit();
  }

  @Override public Unit visitTuple(Pat.@NotNull Tuple lhs, Pat pat) {
    return pat instanceof Pat.Tuple rhs ? visitList(lhs.pats(), rhs.pats()) : reportError(lhs, pat);
  }

  @Override public Unit visitAbsurd(Pat.@NotNull Absurd absurd, Pat pat) {
    throw new IllegalStateException();
  }

  @Override public Unit visitPrim(Pat.@NotNull Prim lhs, Pat pat) {
    if (!(pat instanceof Pat.Prim rhs)) return reportError(lhs, pat);
    assert lhs.ref() == rhs.ref();
    return Unit.unit();
  }

  private Unit visitList(ImmutableSeq<Pat> lpats, ImmutableSeq<Pat> rpats) {
    assert rpats.sizeEquals(lpats.size());
    lpats.zip(rpats).forEach(pp -> unifyPat(pp._1, pp._2, lhsSubst, rhsSubst));
    return Unit.unit();
  }

  private static void visitAs(@Nullable LocalVar as, Pat rhs, PatUnify unifier) {
    if (as == null) return;
    unifier.lhsSubst.add(as, rhs.toTerm());
  }

  private <T> T reportError(@NotNull Pat lhs, @NotNull Pat pat) {
    var doc = Doc.fillSep(lhs.toTerm().toDoc(), Doc.plain("and"), pat.toTerm().toDoc());
    throw new IllegalArgumentException(doc.debugRender() + " are patterns of different types!");
  }

  @Override public Unit visitCtor(Pat.@NotNull Ctor lhs, Pat pat) {
    if (!(pat instanceof Pat.Ctor rhs)) return reportError(lhs, pat);
    // lhs.ref == rhs.ref -- we're assuming this fact!
    assert lhs.ref() == rhs.ref();
    return visitList(lhs.params(), rhs.params());
  }

  private static void unifyPat(Pat lhs, Pat rhs, TermSubst lhsSubst, TermSubst rhsSubst) {
    PatUnify unify;
    if (rhs instanceof Pat.Bind) {
      unify = new PatUnify(rhsSubst, lhsSubst);
      rhs.accept(unify, lhs);
    } else {
      unify = new PatUnify(lhsSubst, rhsSubst);
      lhs.accept(unify, rhs);
    }
    visitAs(lhs.as(), rhs, unify);
    visitAs(rhs.as(), lhs, unify);
  }

  /**
   * The unification of patterns. Assumes well-typedness, homogeneous-ness and positive success.
   *
   * @param lhsSubst the substitutions that would turn the lhs pattern to the rhs one.
   * @param rhsSubst the substitutions that would turn the rhs pattern to the lhs one.
   * @throws IllegalArgumentException if failed
   */
  public static void unifyPat(
    @NotNull SeqLike<Pat> lpats,
    @NotNull SeqLike<Pat> rpats,
    @NotNull TermSubst lhsSubst,
    @NotNull TermSubst rhsSubst
  ) {
    assert rpats.sizeEquals(lpats.size());
    lpats.view().zip(rpats).forEach(pp -> unifyPat(pp._1, pp._2, lhsSubst, rhsSubst));
  }
}
