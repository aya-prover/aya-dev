// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.TermVisitor;
import org.aya.generic.stmt.Shaped;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.def.FnDefLike;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public sealed interface RuleReducer extends Callable.Tele {
  @NotNull default Term make() {
    var result = rule().apply(args());
    return result == null ? this : result;
  }

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
    private @NotNull Term update(@NotNull Shaped.Applicable<FnDefLike> rule, @NotNull ImmutableSeq<Term> args) {
      return args.sameElements(this.args, true) && rule == this.rule
        ? this : new Fn(rule, ulift, args).make();
    }

    @Override public @NotNull Term descent(@NotNull TermVisitor visitor) {
      return update(rule.descent(visitor), Callable.descent(args, visitor));
    }
    public @NotNull FnCall toFnCall() { return new FnCall(rule.ref(), ulift, args); }
  }

  /**
   * A special {@link ConCall} which can be reduced to something interesting.
   */
  record Con(
    @NotNull Shaped.Applicable<ConDefLike> rule,
    int ulift,
    @NotNull ImmutableSeq<Term> ownerArgs,
    @Override @NotNull ImmutableSeq<Term> conArgs
  ) implements RuleReducer, ConCallLike {
    @Override public @NotNull ConCallLike.Head head() {
      return new Head(rule.ref(), this.ulift, ownerArgs);
    }

    @Override public @NotNull ConDefLike ref() { return rule.ref(); }

    public @NotNull Term update(
      @NotNull Shaped.Applicable<ConDefLike> rule,
      @NotNull ImmutableSeq<Term> ownerArgs,
      @NotNull ImmutableSeq<Term> conArgs
    ) {
      return ownerArgs.sameElements(this.ownerArgs, true)
        && conArgs.sameElements(this.conArgs, true)
        && rule == this.rule
        ? this : new Con(rule, ulift, ownerArgs, conArgs).make();
    }

    @Override public @NotNull Term descent(@NotNull TermVisitor visitor) {
      return update(rule.descent(visitor),
        Callable.descent(ownerArgs, visitor), Callable.descent(conArgs, visitor));
    }
  }
}
