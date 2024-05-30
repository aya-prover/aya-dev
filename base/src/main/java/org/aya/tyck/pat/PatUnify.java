// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.pat.PatToTerm;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.TupTerm;
import org.aya.syntax.core.term.call.ConCall;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

/**
 * The unification of patterns. This is <strong>not</strong> pattern unification.
 *
 * @author ice1000
 * @see #unifyPat
 */
public record PatUnify(
  @NotNull MutableList<Term> lhsSubst, @NotNull MutableList<Term> rhsSubst,
  @NotNull LocalCtx ctx
) {
  public record Result(PatUnify unify, @NotNull Seq<Term> args) { }

  private @NotNull Term unify(@NotNull Pat lhs, @NotNull Pat rhs) {
    return switch (lhs) {
      case Pat.Bind bind -> visitAs(bind.bind(), rhs);
      case Pat.Tuple(var elements) -> rhs instanceof Pat.Tuple(var erements)
        ? new TupTerm(visitList(elements, erements))
        : Panic.unreachable();
      case Pat.Con con -> switch (rhs) {
        case Pat.Con con1 -> {
          // Assumption
          assert con.ref() == con1.ref();
          yield new ConCall(con.conHead(), visitList(con.args(), con1.args()));
        }
        case Pat.ShapedInt lit -> unify(con, lit.constructorForm());
        default -> Panic.unreachable();
      };
      case Pat.ShapedInt lhsInt -> switch (rhs) {
        case Pat.ShapedInt _ -> lhsInt.toTerm();
        case Pat.Con con -> unify(lhsInt.constructorForm(), con);
        // Try one more time in case we add more rhs case when lhs is a constructor.
        // see: PatMatcher#match(Pat, Term)
        default -> unify(lhsInt.constructorForm(), rhs);
      };
      default -> Panic.unreachable();
    };
  }

  private @NotNull ImmutableSeq<Term> visitList(ImmutableSeq<Pat> lpats, ImmutableSeq<Pat> rpats) {
    assert rpats.sizeEquals(lpats.size());
    return lpats.zip(rpats, (lp, rp) -> unifyPat(lp, rp, ctx, lhsSubst, rhsSubst));
  }

  private @NotNull Term visitAs(@NotNull LocalVar as, Pat rhs) {
    if (rhs instanceof Pat.Bind(var bind, var ty)) {
      var fresh = new FreeTerm(new LocalVar(as.name() + bind.name()));
      ctx.put(fresh.name(), ty.instantiateTele(rhsSubst.view()));
      lhsSubst.append(fresh);
      rhsSubst.append(fresh);
      return fresh;
    } else {
      rhs.consumeBindings((v, ty) -> {
        ctx.put(v, ty.instantiateTele(rhsSubst.view()));
        rhsSubst.append(new FreeTerm(v));
      });
      var e = PatToTerm.visit(rhs).instantiateTele(rhsSubst.view());
      lhsSubst.append(e);
      return e;
    }
  }

  private static @NotNull Term unifyPat(
    Pat lhs, Pat rhs, LocalCtx ctx,
    MutableList<Term> lhsSubst, MutableList<Term> rhsSubst
  ) {
    if (rhs instanceof Pat.Bind) {
      var unify = new PatUnify(rhsSubst, lhsSubst, ctx);
      return unify.unify(rhs, lhs);
    } else {
      var unify = new PatUnify(lhsSubst, rhsSubst, ctx);
      return unify.unify(lhs, rhs);
    }
  }

  /**
   * The unification of patterns. Assumes well-typedness, homogeneous-ness and positive success.
   *
   * @param lhsSubst the substitutions that would turn the lhs pattern to the rhs one.
   * @param rhsSubst the substitutions that would turn the rhs pattern to the lhs one.
   * @return a ctx that contains all variables that are not unified.
   * @throws Panic if failed
   * @see PatUnify#visitAs(LocalVar, Pat)
   */
  public static @NotNull Result unifyPat(
    @NotNull SeqView<Pat> lpats, @NotNull SeqView<Pat> rpats, @NotNull LocalCtx ctx,
    @NotNull MutableList<Term> lhsSubst, @NotNull MutableList<Term> rhsSubst
  ) {
    assert rpats.sizeEquals(lpats);
    var args = lpats.zip(rpats, (lp, rp) -> unifyPat(lp, rp, ctx, lhsSubst, rhsSubst));
    return new Result(new PatUnify(lhsSubst, rhsSubst, ctx), args.toImmutableSeq());
  }
}
