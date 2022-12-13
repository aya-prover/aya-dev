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
import org.aya.distill.AyaDistillerOptions;
import org.aya.distill.BaseDistiller;
import org.aya.distill.CoreDistiller;
import org.aya.generic.AyaDocile;
import org.aya.generic.Shaped;
import org.aya.generic.util.InternalException;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.tyck.ExprTycker;
import org.aya.tyck.Tycker;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.env.SeqLocalCtx;
import org.aya.tyck.pat.PatTycker;
import org.aya.util.Arg;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Patterns in the core syntax.
 *
 * @author kiva, ice1000, HoshinoTented
 */
@Debug.Renderer(text = "toTerm().toDoc(AyaDistillerOptions.debug()).debugRender()")
public sealed interface Pat extends AyaDocile {
  boolean explicit();
  default @NotNull Term toTerm() {
    return PatToTerm.INSTANCE.visit(this);
  }
  default @NotNull Arg<Term> toArg() {
    return new Arg<>(toTerm(), explicit());
  }
  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return new CoreDistiller(options).pat(this, BaseDistiller.Outer.Free);
  }

  @NotNull Pat zonk(@NotNull Tycker tycker);
  /**
   * Make sure you are inline all patterns in order
   *
   * @param ctx when null, the solutions will not be inlined
   * @return inlined patterns
   */
  @NotNull Pat inline(@Nullable LocalCtx ctx);
  void storeBindings(@NotNull LocalCtx ctx);
  static @NotNull ImmutableSeq<Term.Param> extractTele(@NotNull SeqLike<Pat> pats) {
    var localCtx = new SeqLocalCtx();
    for (var pat : pats) pat.storeBindings(localCtx);
    return localCtx.extract();
  }

  record Bind(
    boolean explicit,
    @NotNull LocalVar bind,
    @NotNull Term type
  ) implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx ctx) {
      ctx.put(bind, type);
    }

    @Override public @NotNull Pat zonk(@NotNull Tycker tycker) {
      return new Bind(explicit, bind, tycker.zonk(type));
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      var newTy = PatTycker.inlineTerm(type);
      if (newTy == type) return this;
      return new Bind(explicit, bind, newTy);
    }
  }


  /**
   * Meta for Hole
   *
   * @param fakeBind is used when inline if there is no solution.
   *                 So don't add this to {@link LocalCtx} too early
   *                 and remember to inline Meta in {@link PatTycker#checkLhs(ExprTycker, Pattern.Clause, Def.Signature, boolean)}
   */
  record Meta(
    boolean explicit,
    @NotNull MutableValue<Pat> solution,
    @NotNull LocalVar fakeBind,
    @NotNull Term type
  ) implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx ctx) {
      // Do nothing
      // This is safe because storeBindings is called only in extractTele which is
      // only used for constructor ownerTele extraction for simpler indexed types
    }

    @Override public @NotNull Pat zonk(@NotNull Tycker tycker) {
      throw new InternalException("unreachable");
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      var value = solution.get();
      if (value == null) {
        var type = PatTycker.inlineTerm(this.type);
        var bind = new Bind(explicit, fakeBind, type);
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

  record Absurd(boolean explicit) implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx ctx) {
      throw new InternalException("unreachable");
    }

    @Override public @NotNull Pat zonk(@NotNull Tycker tycker) {
      return this;
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      return this;
    }
  }

  record Tuple(
    boolean explicit,
    @NotNull ImmutableSeq<Pat> pats
  ) implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx ctx) {
      pats.forEach(pat -> pat.storeBindings(ctx));
    }

    @Override public @NotNull Pat zonk(@NotNull Tycker tycker) {
      return new Tuple(explicit, pats.map(pat -> pat.zonk(tycker)));
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      return new Tuple(explicit, pats.map(p -> p.inline(ctx)));
    }
  }

  record Ctor(
    boolean explicit,
    @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref,
    @NotNull ImmutableSeq<Pat> params,
    @NotNull DataCall type
  ) implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx ctx) {
      params.forEach(pat -> pat.storeBindings(ctx));
    }

    @Override public @NotNull Pat zonk(@NotNull Tycker tycker) {
      return new Ctor(explicit, ref,
        params.map(pat -> pat.zonk(tycker)),
        // The cast must succeed
        (DataCall) tycker.zonk(type));
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      var params = this.params.map(p -> p.inline(ctx));
      return new Ctor(explicit, ref, params, (DataCall) PatTycker.inlineTerm(type));
    }
  }

  record ShapedInt(
    @Override int repr,
    @Override @NotNull ShapeRecognition recognition,
    @NotNull DataCall type,
    boolean explicit
  ) implements Pat, Shaped.Nat<Pat> {

    @Override public @NotNull Pat zonk(@NotNull Tycker tycker) {
      // The cast must succeed
      return new Pat.ShapedInt(repr, recognition, (DataCall) tycker.zonk(type), explicit);
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      // We are no need to inline type here, because the type of Nat doesn't (mustn't) have any type parameter.
      return this;
    }

    @Override public void storeBindings(@NotNull LocalCtx ctx) {
      // do nothing
    }

    @Override public @NotNull Pat makeZero(@NotNull CtorDef zero) {
      return new Pat.Ctor(explicit, zero.ref, ImmutableSeq.empty(), type);
    }

    @Override public @NotNull Pat makeSuc(@NotNull CtorDef suc, @NotNull Arg<Pat> pat) {
      // TODO[ice]: Arg<Pat> in core
      return new Pat.Ctor(explicit, suc.ref, ImmutableSeq.of(pat.term()), type);
    }

    @Override public @NotNull Pat destruct(int repr) {
      return new Pat.ShapedInt(repr, this.recognition, this.type, true);
    }
  }

  /**
   * It's 'pre' because there are also impossible clauses, which are removed after tycking.
   *
   * @author ice1000
   */
  record Preclause<T extends AyaDocile>(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Pat> patterns,
    @NotNull Option<T> expr
  ) implements AyaDocile {
    @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      var distiller = new CoreDistiller(options);
      var pats = options.map.get(AyaDistillerOptions.Key.ShowImplicitPats) ? patterns : patterns.view().filter(Pat::explicit);
      var doc = Doc.emptyIf(pats.isEmpty(), () -> Doc.cat(Doc.ONE_WS, Doc.commaList(
        pats.view().map(p -> distiller.pat(p, BaseDistiller.Outer.Free)))));
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
