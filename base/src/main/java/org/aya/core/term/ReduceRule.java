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

public sealed interface ReduceRule extends Callable.Tele {
  @NotNull Shaped.Appliable<Term, ?, ?> rule();

  /**
   * A {@link Callable} for {@link Shaped.Appliable}.
   *
   * @param rule  should be also a {@link Term}
   * @param ulift
   * @param args
   */
  record Fn(
    @Override @NotNull Shaped.Appliable<Term, FnDef, TeleDecl.FnDecl> rule,
    @Override int ulift,
    @Override @NotNull ImmutableSeq<Arg<Term>> args
  ) implements ReduceRule {
    public Fn {
      assert rule instanceof Term;
    }

    @Override
    public @NotNull DefVar<? extends Def, ? extends TeleDecl<?>> ref() {
      return rule.ref();
    }

    private @NotNull ReduceRule.Fn update(@NotNull Shaped.Appliable<Term, FnDef, TeleDecl.FnDecl> head, @NotNull ImmutableSeq<Arg<Term>> args) {
      return head == this.rule && args.sameElements(this.args, true)
        ? this
        : new Fn(head, ulift, args);
    }

    @Override
    public @NotNull Term descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
      return update((Shaped.Appliable<Term, FnDef, TeleDecl.FnDecl>) f.apply((Term) rule), args.map(x -> x.descent(f)));
    }

    /**
     * @return null if this is not a fn call
     */
    public @NotNull FnCall toFnCall() {
      return new FnCall(rule.ref(), ulift, args);
    }
  }

  /**
   * A special {@link ConCall} which can be reduced to something interesting.
   */
  record Con(
    @NotNull Shaped.Appliable<Term, CtorDef, TeleDecl.DataCtor> rule,
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
