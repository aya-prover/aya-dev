// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.pat;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Subst;
import org.aya.generic.util.InternalException;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.pretty.doc.Doc;
import org.aya.ref.LocalVar;
import org.aya.tyck.env.LocalCtx;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

/**
 * The unification of patterns. This is <strong>not</strong> pattern unification.
 *
 * @author ice1000
 * @see PatUnify#unifyPat(ImmutableSeq, SeqView, SeqView, Subst, Subst, LocalCtx)
 */
public record PatUnify(@NotNull Subst lhsSubst, @NotNull Subst rhsSubst, @NotNull LocalCtx ctx) {
  private void unify(@NotNull Pat lhs, @NotNull Pat rhs) {
    switch (lhs) {
      default -> throw new InternalException();
      case Pat.Bind bind -> visitAs(bind.bind(), rhs);
      case Pat.Meta meta -> visitAs(meta.fakeBind(), rhs);
      case Pat.Tuple tuple -> {
        if (rhs instanceof Pat.Tuple tuple1) visitList(tuple.pats(), tuple1.pats());
        else reportError(lhs, rhs);
      }
      case Pat.Ctor ctor -> {
        switch (rhs) {
          case Pat.Ctor ctor1 -> {
            // Assumption
            assert ctor.ref() == ctor1.ref();
            visitList(ctor.params(), ctor1.params());
          }
          case Pat.ShapedInt lit -> unifyLitWithCtor(ctor, lit, lhs);
          default -> reportError(lhs, rhs);
        }
      }
      case Pat.ShapedInt lhsInt -> {
        switch (rhs) {
          case Pat.ShapedInt rhsInt -> {
            if (!lhsInt.compareUntyped(rhsInt)) reportError(lhs, rhs);
          }
          case Pat.Ctor ctor -> unifyLitWithCtor(ctor, lhsInt, lhs);
          // Try one more time in case we add more rhs case when lhs is a constructor.
          // see: PatMatcher#match(Pat, Term)
          default -> unify(lhsInt.constructorForm(), rhs);
        }
      }
    }
  }

  /** @param lhs marker of left-hand-side for recursion */
  private void unifyLitWithCtor(@NotNull Pat.Ctor ctor, @NotNull Pat.ShapedInt lit, @NotNull Pat lhs) {
    // TODO[literal]: We may convert constructor call to literals to avoid possible stack overflow?
    if (lhs == ctor) unify(ctor, lit.constructorForm());
    if (lhs == lit) unify(lit.constructorForm(), ctor);
  }

  private void visitList(ImmutableSeq<Arg<Pat>> lpats, ImmutableSeq<Arg<Pat>> rpats) {
    assert rpats.sizeEquals(lpats.size());
    lpats.forEachWith(rpats, (lp, rp) -> {
      assert lp.explicit() == rp.explicit();
      unifyPat(lp.term(), rp.term(), lhsSubst, rhsSubst, ctx);
    });
  }

  private void visitAs(@NotNull LocalVar as, Pat rhs) {
    if (rhs instanceof Pat.Bind(var bind, var ty)) {
      var fresh = new LocalVar(as.name() + bind.name());
      ctx.put(fresh, ty.subst(rhsSubst));
      lhsSubst.add(as, new RefTerm(fresh));
      rhsSubst.add(bind, new RefTerm(fresh));
    } else {
      rhs.storeBindings(ctx, rhsSubst);
      lhsSubst.add(as, rhs.toTerm().subst(rhsSubst));
    }
  }

  private void reportError(@NotNull Pat lhs, @NotNull Pat pat) {
    var doc = Doc.sep(lhs.toDoc(AyaPrettierOptions.debug()), Doc.plain("and"), pat.toDoc(AyaPrettierOptions.debug()));
    throw new InternalException(doc.debugRender() + " are patterns of different types!");
  }

  private static void unifyPat(Pat lhs, Pat rhs, Subst lhsSubst, Subst rhsSubst, LocalCtx ctx) {
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
   * @return a ctx that contains all variables that are not unified.
   * @throws IllegalArgumentException if failed
   * @see PatUnify#visitAs(LocalVar, org.aya.core.pat.Pat)
   */
  public static @NotNull LocalCtx unifyPat(
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull SeqView<Pat> lpats,
    @NotNull SeqView<Pat> rpats,
    @NotNull Subst lhsSubst,
    @NotNull Subst rhsSubst,
    @NotNull LocalCtx ctx
  ) {
    assert rpats.sizeEquals(lpats);
    lpats.forEachWith(rpats, (lp, rp) -> unifyPat(lp, rp, lhsSubst, rhsSubst, ctx));
    return ctx;
  }
}
