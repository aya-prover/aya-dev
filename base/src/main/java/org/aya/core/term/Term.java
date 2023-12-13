// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.Map;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple3;
import org.aya.core.UntypedParam;
import org.aya.core.pat.Pat;
import org.aya.core.visitor.*;
import org.aya.generic.AyaDocile;
import org.aya.generic.ParamLike;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.Restr;
import org.aya.prettier.BasePrettier;
import org.aya.prettier.CorePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.ref.AnyVar;
import org.aya.ref.LocalVar;
import org.aya.tyck.tycker.TyckState;
import org.aya.tyck.unify.Synthesizer;
import org.aya.util.Arg;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * A well-typed and terminating term. Once you add a new Term, you should:
 *
 * <ul>
 *   <li>impl {@link org.aya.tyck.ExprTycker}</li>
 *   <li>impl {@link Synthesizer}</li>
 *   <li>impl {@link org.aya.tyck.unify.TermComparator}</li>
 *   <li>impl {@link org.aya.core.pat.PatMatcher}</li>
 *   <li>impl {@link CorePrettier}</li>
 *   <li>impl the corresponding {@link Expander} if your Term is not {@link StableWHNF}</li>
 * </ul>
 *
 * @author ice1000
 */
public sealed interface Term extends AyaDocile, Restr.TermLike<Term>
  permits Callable, CoeTerm, Elimination, Formation, FormulaTerm, HCompTerm, InTerm, MatchTerm, MetaLitTerm, MetaPatTerm, PartialTerm, RefTerm, RefTerm.Field, StableWHNF {
  /**
   * Descending an operation to the term AST. NOTE: Currently we require the operation `f` to preserve:
   * {@link ClassCall}, {@link DataCall}, {@link SortTerm}, {@link org.aya.generic.Shaped.Applicable}.
   */
  @NotNull Term descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g);

  default @NotNull Term subst(@NotNull AnyVar var, @NotNull Term term) {
    return subst(new Subst(var, term));
  }

  default @NotNull Term subst(@NotNull Subst subst) {
    return new EndoTerm.Substituter(subst).apply(this);
  }

  default @NotNull Term subst(@NotNull Map<? extends AnyVar, ? extends Term> subst) {
    return subst(new Subst(MutableMap.from(subst)));
  }

  default @NotNull Term subst(@NotNull Subst subst, int ulift) {
    return this.subst(subst).lift(ulift);
  }

  default @NotNull Term rename() {
    return new EndoTerm.Renamer().apply(this);
  }

  default int findUsages(@NotNull AnyVar var) {
    return new TermFolder.Usages(var).apply(this);
  }

  default VarConsumer.ScopeChecker scopeCheck(@NotNull ImmutableSeq<LocalVar> allowed) {
    var checker = new VarConsumer.ScopeChecker(allowed);
    checker.accept(this);
    assert checker.isCleared() : "The scope checker is not properly cleared up";
    return checker;
  }

  /**
   * @param state used for inlining the holes.
   *              Can be null only if we're absolutely sure that holes are frozen,
   *              like in the error messages.
   */
  default @NotNull Term normalize(@NotNull TyckState state, @NotNull NormalizeMode mode) {
    return switch (mode) {
      case NULL -> this;
      case NF -> new Expander.Normalizer(state).apply(this);
      case WHNF -> new Expander.WHNFer(state).apply(this);
    };
  }

  default @NotNull Term freezeHoles(@Nullable TyckState state) {
    return new EndoTerm() {
      @Override public @NotNull Term pre(@NotNull Term term) {
        return term instanceof MetaTerm hole && state != null
          ? state.metas().getOption(hole.ref()).map(this::pre).getOrDefault(term)
          : term;
      }
    }.apply(this);
  }

  @Override default @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new CorePrettier(options).term(BasePrettier.Outer.Free, this);
  }
  default @NotNull Term lift(int ulift) {
    return new EndoTerm.Elevator(ulift).apply(this);
  }

  /**
   * @author re-xyr
   */
  record Param(
    @Override @NotNull LocalVar ref,
    @Override @NotNull Term type,
    @Override boolean explicit
  ) implements ParamLike<Term>, UntypedParam {
    public Param(@NotNull ParamLike<?> param, @NotNull Term type) {
      this(param.ref(), type, param.explicit());
    }

    public static @NotNull ImmutableSeq<@NotNull Param> fromBuffer(@NotNull MutableList<Tuple3<LocalVar, Boolean, Term>> buf) {
      return buf.view().map(tup -> new Param(tup.component1(), tup.component3(), tup.component2())).toImmutableSeq();
    }

    public @NotNull Param descent(@NotNull UnaryOperator<@NotNull Term> f) {
      var type = f.apply(type());
      if (type == type()) return this;
      return new Param(this, type);
    }

    public void descentConsume(@NotNull Consumer<@NotNull Term> f) {
      f.accept(type);
    }

    @Contract(" -> new") public @NotNull Param implicitify() {
      return new Param(ref, type, false);
    }

    @Contract(" -> new") public @NotNull Param rename() {
      return new Param(renameVar(), type, explicit);
    }

    public @NotNull Arg<Pat> toPat() {
      return new Arg<>(new Pat.Bind(ref, type), explicit);
    }

    public @NotNull Param subst(@NotNull AnyVar var, @NotNull Term term) {
      return subst(new Subst(var, term));
    }

    public @NotNull Param subst(@NotNull Subst subst) {
      return subst(subst, 0);
    }

    public static @NotNull ImmutableSeq<Param> subst(
      @NotNull ImmutableSeq<@NotNull Param> params,
      @NotNull Subst subst, int ulift
    ) {
      return params.map(param -> param.subst(subst, ulift));
    }

    public static @NotNull ImmutableSeq<Param>
    subst(@NotNull ImmutableSeq<@NotNull Param> params, int ulift) {
      return subst(params, Subst.EMPTY, ulift);
    }

    public @NotNull Param subst(@NotNull Subst subst, int ulift) {
      return new Param(ref, type.subst(subst, ulift), explicit);
    }

  }

  record Matching(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Arg<Pat>> patterns,
    @NotNull Term body
  ) implements AyaDocile {
    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      return Pat.Preclause.weaken(this).toDoc(options);
    }

    public @NotNull Matching update(@NotNull ImmutableSeq<Arg<Pat>> patterns, @NotNull Term body) {
      return body == body() && patterns.sameElements(patterns(), true) ? this
        : new Matching(sourcePos, patterns, body);
    }

    public @NotNull Matching descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
      return update(patterns.map(p -> p.descent(g)), f.apply(body));
    }

    public void descentConsume(@NotNull Consumer<Term> f, @NotNull Consumer<Pat> g) {
      patterns.forEach(a -> a.descentConsume(g));
      f.accept(body);
    }
  }
}
