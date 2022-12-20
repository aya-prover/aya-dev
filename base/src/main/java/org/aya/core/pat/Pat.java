// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.pat;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.value.MutableValue;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.repr.ShapeRecognition;
import org.aya.core.term.DataCall;
import org.aya.core.term.Term;
import org.aya.core.visitor.Subst;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.prettier.BasePrettier;
import org.aya.prettier.CorePrettier;
import org.aya.generic.AyaDocile;
import org.aya.generic.Shaped;
import org.aya.generic.util.InternalException;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.tycker.StatedTycker;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.env.SeqLocalCtx;
import org.aya.tyck.pat.PatTycker;
import org.aya.util.Arg;
import org.aya.util.prettier.PrettierOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Patterns in the core syntax.
 *
 * @author kiva, ice1000, HoshinoTented
 */
@Debug.Renderer(text = "toTerm().toDoc(AyaPrettierOptions.debug()).debugRender()")
public sealed interface Pat extends AyaDocile {
  default @NotNull Term toTerm() {
    return PatToTerm.INSTANCE.visit(this);
  }
  @Override default @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new CorePrettier(options).pat(this, true, BasePrettier.Outer.Free);
  }

  @NotNull Pat zonk(@NotNull StatedTycker tycker);
  /**
   * Make sure you are inline all patterns in order
   *
   * @param ctx when null, the solutions will not be inlined
   * @return inlined patterns
   */
  @NotNull Pat inline(@Nullable LocalCtx ctx);
  void storeBindings(@NotNull LocalCtx ctx, @NotNull Subst rhsSubst);
  static @NotNull ImmutableSeq<Term.Param> extractTele(@NotNull SeqLike<Pat> pats) {
    var localCtx = new SeqLocalCtx();
    for (var pat : pats) pat.storeBindings(localCtx, Subst.EMPTY);
    return localCtx.extract();
  }

  record Bind(
    @NotNull LocalVar bind,
    @NotNull Term type
  ) implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx ctx, @NotNull Subst rhsSubst) {
      ctx.put(bind, type.subst(rhsSubst));
    }

    @Override public @NotNull Pat zonk(@NotNull StatedTycker tycker) {
      return new Bind(bind, tycker.zonk(type));
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      var newTy = PatTycker.inlineTerm(type);
      if (newTy == type) return this;
      return new Bind(bind, newTy);
    }
  }


  /**
   * Meta for Hole
   *
   * @param fakeBind is used when inline if there is no solution.
   *                 So don't add this to {@link LocalCtx} too early
   *                 and remember to inline Meta in {@link PatTycker#checkLhs(ExprTycker, Pattern.Clause, Def.Signature, boolean, boolean)}
   */
  record Meta(
    @NotNull MutableValue<Pat> solution,
    @NotNull LocalVar fakeBind,
    @NotNull Term type
  ) implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx ctx, @NotNull Subst rhsSubst) {
      // Do nothing
      // This is safe because storeBindings is called only in extractTele which is
      // only used for constructor ownerTele extraction for simpler indexed types
    }

    @Override public @NotNull Pat zonk(@NotNull StatedTycker tycker) {
      throw new InternalException("unreachable");
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      var value = solution.get();
      if (value == null) {
        var type = PatTycker.inlineTerm(this.type);
        var bind = new Bind(fakeBind, type);
        assert ctx != null : "Pre-inline patterns must be inlined with ctx";
        // We set a solution here, so multiple inline on the same MetaPat is safe.
        solution.set(bind);
        ctx.put(fakeBind, type);
        return bind;
      } else {
        return value.inline(ctx);
      }
    }

  }

  record Absurd() implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx ctx, @NotNull Subst rhsSubst) {
      throw new InternalException("unreachable");
    }

    @Override public @NotNull Pat zonk(@NotNull StatedTycker tycker) {
      return this;
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      return this;
    }
  }

  record Tuple(@NotNull ImmutableSeq<Arg<Pat>> pats) implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx ctx, @NotNull Subst rhsSubst) {
      pats.forEach(pat -> pat.term().storeBindings(ctx, rhsSubst));
    }

    @Override public @NotNull Tuple zonk(@NotNull StatedTycker tycker) {
      return new Tuple(Arg.mapSeq(pats, t -> t.zonk(tycker)));
    }

    @Override public @NotNull Tuple inline(@Nullable LocalCtx ctx) {
      return new Tuple(Arg.mapSeq(pats, t -> t.inline(ctx)));
    }
  }

  record Ctor(
    @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref,
    @NotNull ImmutableSeq<Arg<Pat>> params,
    @NotNull DataCall type
  ) implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx ctx, @NotNull Subst rhsSubst) {
      params.forEach(pat -> pat.term().storeBindings(ctx, rhsSubst));
    }

    @Override public @NotNull Pat zonk(@NotNull StatedTycker tycker) {
      return new Ctor(ref,
        params.map(pat -> pat.descent(x -> x.zonk(tycker))),
        // The cast must succeed
        (DataCall) tycker.zonk(type));
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      var params = this.params.map(p -> p.descent(x -> x.inline(ctx)));
      return new Ctor(ref, params, (DataCall) PatTycker.inlineTerm(type));
    }
  }

  record ShapedInt(
    @Override int repr,
    @Override @NotNull ShapeRecognition recognition,
    @NotNull DataCall type
  ) implements Pat, Shaped.Nat<Pat> {

    @Override public @NotNull Pat zonk(@NotNull StatedTycker tycker) {
      // The cast must succeed
      return new Pat.ShapedInt(repr, recognition, (DataCall) tycker.zonk(type));
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      // We are no need to inline type here, because the type of Nat doesn't (mustn't) have any type parameter.
      return this;
    }

    @Override public void storeBindings(@NotNull LocalCtx ctx, @NotNull Subst rhsSubst) {
      // do nothing
    }

    @Override public @NotNull Pat makeZero(@NotNull CtorDef zero) {
      return new Pat.Ctor(zero.ref, ImmutableSeq.empty(), type);
    }

    @Override public @NotNull Pat makeSuc(@NotNull CtorDef suc, @NotNull Arg<Pat> pat) {
      return new Pat.Ctor(suc.ref, ImmutableSeq.of(pat), type);
    }

    @Override public @NotNull Pat destruct(int repr) {
      return new Pat.ShapedInt(repr, this.recognition, this.type);
    }
  }

  /**
   * It's 'pre' because there are also impossible clauses, which are removed after tycking.
   *
   * @author ice1000
   */
  record Preclause<T extends AyaDocile>(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Arg<Pat>> patterns,
    @NotNull Option<T> expr
  ) implements AyaDocile {
    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      var prettier = new CorePrettier(options);
      var pats = options.map.get(AyaPrettierOptions.Key.ShowImplicitPats) ? patterns : patterns.view().filter(Arg::explicit);
      var doc = Doc.emptyIf(pats.isEmpty(), () -> Doc.cat(Doc.ONE_WS, Doc.commaList(
        pats.view().map(p -> prettier.pat(p, BasePrettier.Outer.Free)))));
      return expr.getOrDefault(it -> Doc.sep(doc, Doc.symbol("=>"), it.toDoc(options)), doc);
    }

    public static @NotNull Preclause<Term> weaken(@NotNull Term.Matching clause) {
      return new Preclause<>(clause.sourcePos(), clause.patterns(), Option.some(clause.body()));
    }

    public static @NotNull Option<Term.@NotNull Matching> lift(@NotNull Preclause<Term> clause) {
      return clause.expr.map(term -> new Term.Matching(clause.sourcePos, clause.patterns, term));
    }
  }
}
