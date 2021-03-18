// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pat;

import org.aya.core.visitor.Substituter.TermSubst;
import org.aya.pretty.doc.Doc;
import org.aya.ref.LocalVar;
import org.aya.tyck.LocalCtx;
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
  @NotNull TermSubst rhsSubst,
  @NotNull LocalCtx localCtx
) implements Pat.Visitor<Pat, Unit> {
  @Override public Unit visitBind(Pat.@NotNull Bind bind, Pat pat) {
    return Unit.unit();
  }

  @Override public Unit visitTuple(Pat.@NotNull Tuple lhs, Pat pat) {
    return pat instanceof Pat.Tuple rhs ? visitList(lhs.pats(), rhs.pats()) : reportError(lhs, pat);
  }

  private Unit visitList(ImmutableSeq<Pat> lpats, ImmutableSeq<Pat> rpats) {
    assert rpats.sizeEquals(lpats.size());
    lpats.zip(rpats).forEach(pp -> unifyPat(pp._1, pp._2, lhsSubst, rhsSubst, localCtx));
    return Unit.unit();
  }

  private static void visitAs(@Nullable LocalVar as, Pat rhs, PatUnify unifier) {
    if (as == null) return;
    unifier.lhsSubst.add(as, rhs.toTerm());
    unifier.localCtx.put(as, rhs.type());
    if (!(rhs instanceof Pat.Bind)) rhs.accept(new PatTyper(unifier.localCtx), Unit.unit());
  }

  private <T> T reportError(@NotNull Pat lhs, @NotNull Pat pat) {
    var doc = Doc.hcat(lhs.toTerm().toDoc(), Doc.plain(" and "), pat.toTerm().toDoc());
    throw new IllegalArgumentException(doc.renderWithPageWidth(1000) + " are patterns of different types!");
  }

  @Override public Unit visitCtor(Pat.@NotNull Ctor lhs, Pat pat) {
    if (!(pat instanceof Pat.Ctor rhs)) return reportError(lhs, pat);
    // lhs.dataRef == rhs.dataRef -- we're assuming this fact!
    assert lhs.ref() == rhs.ref();
    return visitList(lhs.params(), rhs.params());
  }

  private static void unifyPat(Pat lhs, Pat rhs, TermSubst lhsSubst, TermSubst rhsSubst, LocalCtx localCtx) {
    PatUnify unify;
    if (rhs instanceof Pat.Bind) {
      unify = new PatUnify(rhsSubst, lhsSubst, localCtx);
      rhs.accept(unify, lhs);
    } else {
      unify = new PatUnify(lhsSubst, rhsSubst, localCtx);
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
   * @return the context containing all the variable bindings in <code>lhsSubst</code> and <code>rhsSubst</code>
   * @throws IllegalArgumentException if failed
   */
  public static @NotNull LocalCtx unifyPat(
    @NotNull SeqLike<Pat> lpats,
    @NotNull SeqLike<Pat> rpats,
    @NotNull TermSubst lhsSubst,
    @NotNull TermSubst rhsSubst
  ) {
    assert rpats.sizeEquals(lpats.size());
    var ctx = new LocalCtx();
    lpats.view().zip(rpats).forEach(pp -> unifyPat(pp._1, pp._2, lhsSubst, rhsSubst, ctx));
    return ctx;
  }
}
