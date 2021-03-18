// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pat;

import org.aya.api.ref.Var;
import org.aya.core.visitor.Substituter.TermSubst;
import org.aya.pretty.doc.Doc;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.tuple.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The unification of patterns. This is <strong>not</strong> pattern unification.
 *
 * @author ice1000
 * @see PatUnify#unifyPat(SeqLike, SeqLike, TermSubst, TermSubst)
 */
public record PatUnify(
  @NotNull TermSubst lhsSubst,
  @NotNull TermSubst rhsSubst
) implements Pat.Visitor<Pat, Unit> {
  @Override public Unit visitBind(Pat.@NotNull Bind bind, Pat pat) {
    lhsSubst.add(bind.as(), pat.toTerm());
    return Unit.unit();
  }

  @Override public Unit visitTuple(Pat.@NotNull Tuple lhs, Pat pat) {
    return pat instanceof Pat.Tuple rhs ? visitList(lhs.pats(), rhs.pats(), lhs.as(), rhs) : reportError(lhs, pat);
  }

  private Unit visitList(ImmutableSeq<Pat> lpats, ImmutableSeq<Pat> rpats, @Nullable Var as, Pat rhs) {
    if (as != null) lhsSubst.add(as, rhs.toTerm());
    assert rpats.sizeEquals(lpats.size());
    lpats.zip(rpats).forEach(pp -> unifyPat(pp._1, pp._2, lhsSubst, rhsSubst));
    return Unit.unit();
  }

  private <T> T reportError(@NotNull Pat lhs, @NotNull Pat pat) {
    var doc = Doc.hcat(lhs.toTerm().toDoc(), Doc.plain(" and "), pat.toTerm().toDoc());
    throw new IllegalArgumentException(doc.renderWithPageWidth(1000) + " are patterns of different types!");
  }

  @Override public Unit visitCtor(Pat.@NotNull Ctor lhs, Pat pat) {
    if (!(pat instanceof Pat.Ctor rhs)) return reportError(lhs, pat);
    // lhs.dataRef == rhs.dataRef -- we're assuming this fact!
    assert lhs.ref() == rhs.ref();
    return visitList(lhs.params(), rhs.params(), lhs.as(), rhs);
  }

  public static void unifyPat(@NotNull Pat lhs, @NotNull Pat rhs, TermSubst lhsSubst, TermSubst rhsSubst) {
    if (rhs instanceof Pat.Bind) rhs.accept(new PatUnify(rhsSubst, lhsSubst), lhs);
    else lhs.accept(new PatUnify(lhsSubst, rhsSubst), rhs);
  }

  /**
   * The unification of patterns. Assumes well-typedness, homogeneous-ness and positive success.
   *
   * @param lhsSubst the substitutions that would turn the lhs pattern to the rhs one.
   * @param rhsSubst the substitutions that would turn the rhs pattern to the lhs one.
   * @throws IllegalArgumentException if failed
   */
  public static void unifyPat(@NotNull SeqLike<Pat> lpats, @NotNull SeqLike<Pat> rpats, TermSubst lhsSubst, TermSubst rhsSubst) {
    assert rpats.sizeEquals(lpats.size());
    lpats.view().zip(rpats).forEach(pp -> unifyPat(pp._1, pp._2, lhsSubst, rhsSubst));
  }
}
