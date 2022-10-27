// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.pat;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import kala.value.MutableValue;
import org.aya.concrete.Expr;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.Matching;
import org.aya.core.def.CtorDef;
import org.aya.core.repr.AyaShape;
import org.aya.core.term.CallTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Subst;
import org.aya.distill.BaseDistiller;
import org.aya.distill.CoreDistiller;
import org.aya.generic.Arg;
import org.aya.generic.AyaDocile;
import org.aya.generic.Shaped;
import org.aya.generic.util.InternalException;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.tyck.TyckState;
import org.aya.tyck.Tycker;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.env.SeqLocalCtx;
import org.aya.util.distill.DistillerOptions;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kiva, ice1000
 */
@Debug.Renderer(text = "toTerm().toDoc(DistillerOptions.debug()).debugRender()")
public sealed interface Pat extends AyaDocile {
  boolean explicit();
  default @NotNull Term toTerm() {
    return PatToTerm.INSTANCE.visit(this);
  }
  @NotNull Expr toExpr(@NotNull SourcePos pos);
  default @NotNull Arg<Term> toArg() {
    return new Arg<>(toTerm(), explicit());
  }
  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return new CoreDistiller(options).pat(this, BaseDistiller.Outer.Free);
  }
  @NotNull Pat rename(@NotNull Subst subst, @NotNull LocalCtx localCtx, boolean explicit);
  @NotNull Pat zonk(@NotNull Tycker tycker);
  /**
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

    @Override public @NotNull Expr toExpr(@NotNull SourcePos pos) {
      return new Expr.RefExpr(pos, bind);
    }

    @Override
    public @NotNull Pat rename(@NotNull Subst subst, @NotNull LocalCtx localCtx, boolean explicit) {
      var newName = new LocalVar(bind.name(), bind.definition());
      var newBind = new Bind(explicit, newName, type.subst(subst));
      subst.addDirectly(bind, new RefTerm(newName));
      localCtx.put(newName, type);
      return newBind;
    }

    @Override public @NotNull Pat zonk(@NotNull Tycker tycker) {
      return new Bind(explicit, bind, tycker.zonk(type));
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      return this;
    }
  }

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

    @Override public @NotNull Expr toExpr(@NotNull SourcePos pos) {
      return new Expr.MetaPat(pos, this);
    }

    @Override public @NotNull Pat zonk(@NotNull Tycker tycker) {
      throw new InternalException("unreachable");
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      var value = solution.get();
      if (value == null) {
        var bind = new Bind(explicit, fakeBind, type);
        assert ctx != null : "Pre-inline patterns must be inlined with ctx";
        solution.set(bind);
        ctx.put(fakeBind, type);
        return bind;
      } else return value;
    }

    @Override
    public @NotNull Pat rename(@NotNull Subst subst, @NotNull LocalCtx localCtx, boolean explicit) {
      throw new InternalException("unreachable");
    }
  }

  record Absurd(boolean explicit) implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx ctx) {
      throw new InternalException("unreachable");
    }

    @Override public @NotNull Expr toExpr(@NotNull SourcePos pos) {
      // [ice]: this code is reachable (to substitute a telescope), but the telescope will be dropped anyway.
      return new Expr.RefExpr(pos, new LocalVar("()", SourcePos.NONE));
    }

    @Override
    public @NotNull Pat rename(@NotNull Subst subst, @NotNull LocalCtx localCtx, boolean explicit) {
      throw new InternalException();
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

    @Override public @NotNull Expr toExpr(@NotNull SourcePos pos) {
      return new Expr.TupExpr(pos, pats.map(pat -> pat.toExpr(pos)));
    }

    @Override
    public @NotNull Pat rename(@NotNull Subst subst, @NotNull LocalCtx localCtx, boolean explicit) {
      var params = pats.map(pat -> pat.rename(subst, localCtx, pat.explicit()));
      return new Tuple(explicit, params);
    }

    @Override public @NotNull Pat zonk(@NotNull Tycker tycker) {
      return new Tuple(explicit, pats.map(pat -> pat.zonk(tycker)));
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      return new Tuple(explicit, pats.map(p -> p.inline(ctx)));
    }
  }

  /**
   * @param ownerArgs the arguments to the owner telescope of this Ctor.
   */
  record Ctor(
    boolean explicit,
    @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref,
    @NotNull ImmutableSeq<Term> ownerArgs,
    @NotNull ImmutableSeq<Pat> params,
    @NotNull CallTerm.Data type
  ) implements Pat {
    @Override public void storeBindings(@NotNull LocalCtx ctx) {
      // ownerArgs are not pattern, so they don't introduce any binding.
      params.forEach(pat -> pat.storeBindings(ctx));
    }

    @Override public @NotNull Expr toExpr(@NotNull SourcePos pos) {
      return params.foldLeft((Expr) new Expr.RefExpr(pos, ref), (f, pat) -> new Expr.AppExpr(pos, f,
        new Expr.NamedArg(pat.explicit(), pat.toExpr(pos))));
    }

    @Override
    public @NotNull Pat rename(@NotNull Subst subst, @NotNull LocalCtx localCtx, boolean explicit) {
      var params = this.params.map(pat -> pat.rename(subst, localCtx, pat.explicit()));
      return new Ctor(explicit, ref, 
        ownerArgs.map(x -> x.subst(subst)),
        params, (CallTerm.Data) type.subst(subst));
    }

    @Override public @NotNull Pat zonk(@NotNull Tycker tycker) {
      return new Ctor(explicit, ref,
        ownerArgs.map(tycker::zonk),
        params.map(pat -> pat.zonk(tycker)),
      // The cast must succeed
        (CallTerm.Data) tycker.zonk(type));
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      return new Ctor(explicit, ref, ownerArgs(), params.map(p -> p.inline(ctx)), type);
    }
  }

  record End(boolean isOne, boolean explicit) implements Pat {
    @Override public @NotNull Expr toExpr(@NotNull SourcePos pos) {
      return new Expr.LitIntExpr(pos, isOne ? 1 : 0);
    }

    @Override public @NotNull Pat rename(@NotNull Subst subst, @NotNull LocalCtx localCtx, boolean explicit) {
      return this;
    }

    @Override
    public @NotNull Pat zonk(@NotNull Tycker tycker) {
      return this;
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      return this;
    }

    @Override public void storeBindings(@NotNull LocalCtx ctx) {
      // do nothing
    }
  }

  /**
   * TODO[literal]: literal type needs meta-solving for first-class patterns. Possible changes:
   *  - Make {@link ShapedInt#type} a {@link Term} instead of {@link CallTerm.Data}
   *  - Call {@link ShapedInt#constructorForm()} with a {@link TyckState}
   *  - Call {@link #compareUntyped(Shaped)} with a {@link TyckState}
   *  see <a href="https://github.com/aya-prover/aya-dev/pull/400#discussion_r862371935">discussion</a>
   */
  record ShapedInt(
    @Override int repr,
    @Override @NotNull AyaShape shape,
    @NotNull CallTerm.Data type,
    boolean explicit
  ) implements Pat, Shaped.Nat<Pat> {
    @Override public @NotNull Expr toExpr(@NotNull SourcePos pos) {
      return new Expr.LitIntExpr(pos, repr);
    }

    @Override public @NotNull Pat rename(@NotNull Subst subst, @NotNull LocalCtx localCtx, boolean explicit) {
      return this;
    }

    @Override public @NotNull Pat zonk(@NotNull Tycker tycker) {
      // The cast must succeed
      return new Pat.ShapedInt(repr, shape, (CallTerm.Data) tycker.zonk(type), explicit);
    }

    @Override public @NotNull Pat inline(@Nullable LocalCtx ctx) {
      return this;
    }

    @Override public void storeBindings(@NotNull LocalCtx ctx) {
      // do nothing
    }

    @Override public @NotNull Pat makeZero(@NotNull CtorDef zero) {
      return new Pat.Ctor(explicit, zero.ref, ImmutableSeq.empty(), ImmutableSeq.empty(), type);
    }

    @Override public @NotNull Pat makeSuc(@NotNull CtorDef suc, @NotNull Pat pat) {
      return new Pat.Ctor(explicit, suc.ref, ImmutableSeq.empty(), ImmutableSeq.of(pat), type);
    }

    @Override public @NotNull Pat destruct(int repr) {
      return new Pat.ShapedInt(repr, this.shape, this.type, true);
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
      var pats = options.map.get(DistillerOptions.Key.ShowImplicitPats) ? patterns : patterns.view().filter(Pat::explicit);
      var doc = Doc.emptyIf(pats.isEmpty(), () -> Doc.cat(Doc.ONE_WS, Doc.commaList(
        pats.view().map(p -> distiller.pat(p, BaseDistiller.Outer.Free)))));
      return expr.getOrDefault(it -> Doc.sep(doc, Doc.symbol("=>"), it.toDoc(options)), doc);
    }

    public static @NotNull Preclause<Term> weaken(@NotNull Matching clause) {
      return new Preclause<>(clause.sourcePos(), clause.patterns(), Option.some(clause.body()));
    }

    public static @NotNull Option<@NotNull Matching> lift(@NotNull Preclause<Term> clause) {
      return clause.expr.map(term -> new Matching(clause.sourcePos, clause.patterns, term));
    }
  }
}
