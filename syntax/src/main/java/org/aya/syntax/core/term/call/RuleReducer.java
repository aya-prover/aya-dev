// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.generic.stmt.Shaped;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.def.FnDefLike;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public sealed interface RuleReducer extends Callable.Tele {
  @NotNull Shaped.Applicable<?> rule();
  @Override default @NotNull AnyDef ref() { return rule().ref(); }

  /**
   * A {@link Callable} for {@link Shaped.Applicable}.
   *
   * @param ulift
   * @param args
   */
  record Fn(
    @Override @NotNull Shaped.Applicable<FnDefLike> rule,
    @Override int ulift,
    @Override @NotNull ImmutableSeq<Term> args
  ) implements RuleReducer {
    private @NotNull Fn update(@NotNull Shaped.Applicable<FnDefLike> rule, @NotNull ImmutableSeq<Term> args) {
      return args.sameElements(this.args, true) && rule == this.rule
        ? this : new Fn(rule, ulift, args);
    }

    @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
      return update(rule.descent(f), Callable.descent(args, f));
    }
    public @NotNull FnCall toFnCall() { return new FnCall(rule.ref(), ulift, args); }
  }

  /**
   * A special {@link ConCall} which can be reduced to something interesting.
   */
  record Con(
    @NotNull Shaped.Applicable<ConDefLike> rule,
    int ulift,
    @NotNull ImmutableSeq<Term> dataArgs,
    @Override @NotNull ImmutableSeq<Term> conArgs
  ) implements RuleReducer, ConCallLike {
    @Override public @NotNull ConCallLike.Head head() {
      return new Head(rule.ref(), this.ulift, dataArgs);
    }

    @Override public @NotNull ConDefLike ref() { return rule.ref(); }

    public @NotNull RuleReducer.Con update(
      @NotNull Shaped.Applicable<ConDefLike> rule,
      @NotNull ImmutableSeq<Term> dataArgs,
      @NotNull ImmutableSeq<Term> conArgs
    ) {
      return dataArgs.sameElements(this.dataArgs, true)
        && conArgs.sameElements(this.conArgs, true)
        && rule == this.rule
        ? this : new Con(rule, ulift, dataArgs, conArgs);
    }

    @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
      return update(rule.descent(f),
        Callable.descent(dataArgs, f), Callable.descent(conArgs, f));
    }
  }
}
