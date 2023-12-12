// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.pat;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.value.MutableValue;
import org.aya.concrete.Pattern;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.repr.ShapeRecognition;
import org.aya.core.term.DataCall;
import org.aya.core.term.Term;
import org.aya.core.visitor.Subst;
import org.aya.generic.AyaDocile;
import org.aya.generic.Shaped;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.prettier.BasePrettier;
import org.aya.prettier.CorePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.env.SeqLocalCtx;
import org.aya.tyck.pat.ClauseTycker;
import org.aya.tyck.tycker.ConcreteAwareTycker;
import org.aya.util.Arg;
import org.aya.util.error.InternalException;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;

/**
 * Patterns in the core syntax.
 *
 * @author kiva, ice1000, HoshinoTented
 */
@Debug.Renderer(text = "toTerm().toDoc(AyaPrettierOptions.debug()).debugRender()")
public sealed interface Pat extends AyaDocile {
  @NotNull Pat descent(@NotNull UnaryOperator<Pat> f, @NotNull UnaryOperator<Term> g);
  default @NotNull Term toTerm() {
    return PatToTerm.INSTANCE.visit(this);
  }
  @Override default @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new CorePrettier(options).pat(this, true, BasePrettier.Outer.Free);
  }

  @NotNull Pat zonk(@NotNull ConcreteAwareTycker tycker);
  /**
   * Make sure you inline all patterns in order
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
    public @NotNull Bind update(@NotNull Term type) {
      return type == type() ? this : new Bind(bind, type);
    }

    @Override public @NotNull Bind descent(@NotNull UnaryOperator<Pat> f, @NotNull UnaryOperator<Term> g) {
      return update(g.apply(type));
    }

    @Override public void storeBindings(@NotNull LocalCtx ctx, @NotNull Subst rhsSubst) {
      ctx.put(bind, type.subst(rhsSubst));
    }

    @Override public @NotNull Pat zonk(@NotNull ConcreteAwareTycker tycker) {
      return new Bind(bind, tycker.zonk(type));
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      var newTy = ClauseTycker.inlineTerm(type);
      if (newTy == type) return this;
      return new Bind(bind, newTy);
    }
  }


  /**
   * Meta for Hole
   *
   * @param fakeBind is used when inline if there is no solution.
   *                 So don't add this to {@link LocalCtx} too early
   *                 and remember to inline Meta in {@link ClauseTycker#checkLhs(ExprTycker, Pattern.Clause, Def.Signature, boolean, boolean)}
   */
  record Meta(
    @NotNull MutableValue<Pat> solution,
    @NotNull LocalVar fakeBind,
    @NotNull Term type
  ) implements Pat {
    public @NotNull Meta update(@NotNull Pat solution, @NotNull Term type) {
      return solution == solution().get() && type == type()
        ? this : new Meta(MutableValue.create(solution), fakeBind, type);
    }

    @Override public @NotNull Meta descent(@NotNull UnaryOperator<Pat> f, @NotNull UnaryOperator<Term> g) {
      var solution = solution().get();
      return solution == null ? this : update(f.apply(solution), g.apply(type));
    }

    @Override public void storeBindings(@NotNull LocalCtx ctx, @NotNull Subst rhsSubst) {
      // Do nothing
      // This is safe because storeBindings is called only in extractTele which is
      // only used for constructor ownerTele extraction for simpler indexed types
    }

    @Override public @NotNull Pat zonk(@NotNull ConcreteAwareTycker tycker) {
      throw new InternalException("unreachable");
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      var value = solution.get();
      if (value == null) {
        var type = ClauseTycker.inlineTerm(this.type);
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

  enum Absurd implements Pat {
    INSTANCE;

    @Override public @NotNull Absurd descent(@NotNull UnaryOperator<Pat> f, @NotNull UnaryOperator<Term> g) {
      return this;
    }

    @Override public void storeBindings(@NotNull LocalCtx ctx, @NotNull Subst rhsSubst) {
      throw new InternalException("unreachable");
    }

    @Override public @NotNull Pat zonk(@NotNull ConcreteAwareTycker tycker) {
      return this;
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      return this;
    }
  }

  record Tuple(@NotNull ImmutableSeq<Arg<Pat>> pats) implements Pat {
    public @NotNull Tuple update(@NotNull ImmutableSeq<Arg<Pat>> pats) {
      return pats.sameElements(pats(), true) ? this : new Tuple(pats);
    }

    @Override public @NotNull Tuple descent(@NotNull UnaryOperator<Pat> f, @NotNull UnaryOperator<Term> g) {
      return update(pats.map(a -> a.descent(f)));
    }

    @Override public void storeBindings(@NotNull LocalCtx ctx, @NotNull Subst rhsSubst) {
      pats.forEach(pat -> pat.term().storeBindings(ctx, rhsSubst));
    }

    @Override public @NotNull Tuple zonk(@NotNull ConcreteAwareTycker tycker) {
      return new Tuple(Arg.mapSeq(pats, t -> t.zonk(tycker)));
    }

    @Override public @NotNull Tuple inline(@Nullable LocalCtx ctx) {
      return new Tuple(Arg.mapSeq(pats, t -> t.inline(ctx)));
    }
  }

  record Ctor(
    @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref,
    @NotNull ImmutableSeq<Arg<Pat>> params,
    @Nullable ShapeRecognition typeRecog,
    @NotNull DataCall type
  ) implements Pat {
    public @NotNull Ctor update(@NotNull ImmutableSeq<Arg<Pat>> params, @NotNull DataCall type) {
      return type == type() && params.sameElements(params(), true) ? this : new Ctor(ref, params, typeRecog, type);
    }

    @Override public @NotNull Ctor descent(@NotNull UnaryOperator<Pat> f, @NotNull UnaryOperator<Term> g) {
      return update(params.map(p -> p.descent(f)), (DataCall) g.apply(type));
    }

    @Override public void storeBindings(@NotNull LocalCtx ctx, @NotNull Subst rhsSubst) {
      params.forEach(pat -> pat.term().storeBindings(ctx, rhsSubst));
    }

    @Override public @NotNull Pat zonk(@NotNull ConcreteAwareTycker tycker) {
      return new Ctor(ref,
        params.map(pat -> pat.descent(x -> x.zonk(tycker))),
        typeRecog,
        // The cast must succeed
        (DataCall) tycker.zonk(type));
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      var params = this.params.map(p -> p.descent(x -> x.inline(ctx)));
      return new Ctor(ref, params, typeRecog, (DataCall) ClauseTycker.inlineTerm(type));
    }
  }

  record ShapedInt(
    @Override int repr,
    @Override @NotNull ShapeRecognition recognition,
    @NotNull DataCall type
  ) implements Pat, Shaped.Nat<Pat> {
    public ShapedInt update(DataCall type) {
      return type == type() ? this : new ShapedInt(repr, recognition, type);
    }

    @Override public @NotNull ShapedInt descent(@NotNull UnaryOperator<Pat> f, @NotNull UnaryOperator<Term> g) {
      return update((DataCall) g.apply(type));
    }

    @Override public @NotNull Pat zonk(@NotNull ConcreteAwareTycker tycker) {
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
      return new Pat.Ctor(zero.ref, ImmutableSeq.empty(), recognition, type);
    }

    @Override public @NotNull Pat makeSuc(@NotNull CtorDef suc, @NotNull Arg<Pat> pat) {
      return new Pat.Ctor(suc.ref, ImmutableSeq.of(pat), recognition, type);
    }

    @Override public @NotNull Pat destruct(int repr) {
      return new Pat.ShapedInt(repr, this.recognition, this.type);
    }

    @Override
    public @NotNull ShapedInt map(@NotNull IntUnaryOperator f) {
      return new ShapedInt(f.applyAsInt(repr), recognition, type);
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
