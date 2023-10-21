// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.CtorDef;
import org.aya.core.def.Def;
import org.aya.core.def.FnDef;
import org.aya.core.pat.Pat;
import org.aya.generic.Shaped;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * Maybe we should call this a RuleReducer.
 */
public sealed interface ReduceRule extends Callable.Tele {
  @NotNull Shaped.Applicable<Term, ?, ?> rule();

  /**
   * A {@link Callable} for {@link Shaped.Applicable}.
   *
   * @param ulift
   * @param args
   */
  record Fn(
    @Override @NotNull Shaped.Applicable<Term, FnDef, TeleDecl.FnDecl> rule,
    @Override int ulift,
    @Override @NotNull ImmutableSeq<Arg<Term>> args
  ) implements ReduceRule {
    @Override
    public @NotNull DefVar<? extends Def, ? extends TeleDecl<?>> ref() {
      return rule.ref();
    }

    private @NotNull ReduceRule.Fn update(@NotNull ImmutableSeq<Arg<Term>> args) {
      return args.sameElements(this.args, true)
        ? this
        : new Fn(rule, ulift, args);
    }

    @Override
    public @NotNull Term descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
      return update(args.map(x -> x.descent(f)));
    }

    public @NotNull FnCall toFnCall() {
      return new FnCall(rule.ref(), ulift, args);
    }
  }

  /**
   * A special {@link ConCall} which can be reduced to something interesting.
   */
  record Con(
    @NotNull Shaped.Applicable<Term, CtorDef, TeleDecl.DataCtor> rule,
    int ulift,
    @NotNull ImmutableSeq<Arg<Term>> dataArgs,
    @Override @NotNull ImmutableSeq<Arg<Term>> conArgs

  ) implements ReduceRule, ConCallLike {
    @Override
    public @NotNull ConCallLike.Head head() {
      return new Head(
        rule.ref().core.dataRef,
        rule.ref(),
        this.ulift,
        dataArgs
      );
    }

    public @NotNull ReduceRule.Con update(
      @NotNull ImmutableSeq<Arg<Term>> dataArgs,
      @NotNull ImmutableSeq<Arg<Term>> conArgs
    ) {
      return dataArgs.sameElements(this.dataArgs, true) && conArgs.sameElements(this.conArgs, true)
        ? this
        : new Con(rule, ulift, dataArgs, conArgs);
    }

    @Override
    public @NotNull Term descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
      return update(dataArgs.map(x -> x.descent(f)), conArgs.map(x -> x.descent(f)));
    }
  }
}
