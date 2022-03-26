// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.pat;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import org.aya.core.visitor.Substituter.TermSubst;
import org.aya.pretty.doc.Doc;
import org.aya.ref.LocalVar;
import org.aya.tyck.env.LocalCtx;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;

/**
 * The unification of patterns. This is <strong>not</strong> pattern unification.
 *
 * @author ice1000
 * @see PatUnify#unifyPat(SeqLike, SeqLike, TermSubst, TermSubst, LocalCtx)
 */
public record PatUnify(@NotNull TermSubst lhsSubst, @NotNull TermSubst rhsSubst, @NotNull LocalCtx ctx) {
  private void unify(@NotNull Pat lhs, @NotNull Pat rhs) {
    switch (lhs) {
      default -> throw new IllegalStateException();
      case Pat.Bind bind -> visitAs(bind.bind(), rhs);
      case Pat.Meta meta -> visitAs(meta.fakeBind(), rhs);
      case Pat.Tuple tuple -> {
        if (rhs instanceof Pat.Tuple tuple1) visitList(tuple.pats(), tuple1.pats());
        else reportError(lhs, rhs);
      }
      case Pat.Prim prim -> {
        if (!(rhs instanceof Pat.Prim prim1)) reportError(lhs, rhs);
        else assert prim.ref() == prim1.ref();
      }
      case Pat.Ctor ctor -> {
        if (rhs instanceof Pat.Ctor ctor1) {
          // Assumption
          assert ctor.ref() == ctor1.ref();
          visitList(ctor.params(), ctor1.params());
        } else reportError(lhs, rhs);
      }
    }
  }

  private void visitList(ImmutableSeq<Pat> lpats, ImmutableSeq<Pat> rpats) {
    assert rpats.sizeEquals(lpats.size());
    lpats.zip(rpats).forEach(pp -> unifyPat(pp._1, pp._2, lhsSubst, rhsSubst, ctx));
  }

  private void visitAs(@NotNull LocalVar as, Pat rhs) {
    if (rhs instanceof Pat.Bind bind) ctx.put(bind.bind(), bind.type());
    else rhs.storeBindings(ctx);
    lhsSubst.add(as, rhs.toTerm());
  }

  private void reportError(@NotNull Pat lhs, @NotNull Pat pat) {
    var doc = Doc.sep(lhs.toDoc(DistillerOptions.debug()), Doc.plain("and"), pat.toDoc(DistillerOptions.debug()));
    throw new IllegalArgumentException(doc.debugRender() + " are patterns of different types!");
  }

  private static void unifyPat(Pat lhs, Pat rhs, TermSubst lhsSubst, TermSubst rhsSubst, LocalCtx ctx) {
    PatUnify unify;
    if (rhs instanceof Pat.Bind) {
      unify = new PatUnify(rhsSubst, lhsSubst, ctx);
      unify.unify(rhs, lhs);
    } else {
      unify = new PatUnify(lhsSubst, rhsSubst, ctx);
      unify.unify(lhs, rhs);
    }
  }

  /**
   * The unification of patterns. Assumes well-typedness, homogeneous-ness and positive success.
   *
   * @param lhsSubst the substitutions that would turn the lhs pattern to the rhs one.
   * @param rhsSubst the substitutions that would turn the rhs pattern to the lhs one.
   * @param ctx
   * @return a ctx that contains all variables that are not unified.
   * @throws IllegalArgumentException if failed
   * @see PatUnify#visitAs(LocalVar, org.aya.core.pat.Pat)
   */
  public static @NotNull LocalCtx unifyPat(
    @NotNull SeqLike<Pat> lpats,
    @NotNull SeqLike<Pat> rpats,
    @NotNull TermSubst lhsSubst,
    @NotNull TermSubst rhsSubst,
    @NotNull LocalCtx ctx
  ) {
    assert rpats.sizeEquals(lpats);
    lpats.view().zip(rpats).forEach(pp -> unifyPat(pp._1, pp._2, lhsSubst, rhsSubst, ctx));
    return ctx;
  }
}
