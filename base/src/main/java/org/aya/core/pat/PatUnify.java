// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.pat;

import org.aya.api.ref.Var;
import org.aya.core.visitor.Substituter;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.control.Option;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The unification of patterns. Assumes well-typedness.
 *
 * @author ice1000
 */
public class PatUnify implements Pat.Visitor<Pat, Substituter.TermSubst> {
  private static final @NotNull PatUnify INSTANCE = new PatUnify();

  private PatUnify() {
  }

  @Override public Substituter.TermSubst visitBind(Pat.@NotNull Bind bind, Pat pat) {
    return new Substituter.TermSubst(bind.as(), pat.toTerm());
  }

  @Override public Substituter.TermSubst visitTuple(Pat.@NotNull Tuple lhs, Pat pat) {
    var lpats = lhs.pats();
    if (!(pat instanceof Pat.Tuple rhs)) return reportError(lhs, pat);
    var rpats = rhs.pats();
    assert rpats.sizeEquals(lpats.size());
    return visitList(lpats, rpats, lhs.as(), rhs);
  }

  private Substituter.@NotNull TermSubst
  visitList(ImmutableSeq<Pat> lpats, ImmutableSeq<Pat> rpats, @Nullable Var as, Pat rhs) {
    var init = new Substituter.TermSubst(new MutableHashMap<>());
    if (as != null) init.add(as, rhs.toTerm());
    return lpats.zip(rpats).foldLeft(init,
      (subst, pp) -> subst.add(pp._1.accept(this, pp._2)));
  }

  private <T> T reportError(@NotNull Pat lhs, @NotNull Pat pat) {
    var lhsErr = lhs.toTerm().toDoc().renderWithPageWidth(1000);
    var rhsErr = pat.toTerm().toDoc().renderWithPageWidth(1000);
    throw new IllegalArgumentException(lhsErr + " and " + rhsErr + " are patterns of different types!");
  }

  @Override public Substituter.TermSubst visitCtor(Pat.@NotNull Ctor lhs, Pat pat) {
    if (!(pat instanceof Pat.Ctor rhs)) return reportError(lhs, pat);
    // lhs.dataRef == rhs.dataRef -- we're assuming this fact!
    if (lhs.ref() != rhs.ref()) throw new NegativeSuccess();
    var init = new Substituter.TermSubst(new MutableHashMap<>());
    if (lhs.as() != null) init.add(lhs.as(), rhs.toTerm());
    return visitList(lhs.params(), rhs.params(), lhs.as(), rhs);
  }

  /**
   * The unification of patterns. Assumes well-typedness and homogeneous-ness.
   *
   * @param lhs Can contain `as` patterns
   * @param rhs Preferably the internally generated patterns
   * @return Some(Subst) if success positively, or None if success negatively
   * @throws IllegalArgumentException if failed
   */
  public static Option<Substituter.TermSubst> unify(@NotNull Pat lhs, @NotNull Pat rhs) {
    try {
      var res = lhs.accept(INSTANCE, rhs);
      return Option.some(res);
    } catch (NegativeSuccess negative) {
      return Option.none();
    }
  }

  private static class NegativeSuccess extends RuntimeException {
  }
}
