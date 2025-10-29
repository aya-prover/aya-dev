// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.function.IndexedFunction;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record AppTerm(@NotNull Term fun, @NotNull Term arg) implements BetaRedex {
  public @NotNull Term update(@NotNull Term fun, @NotNull Term arg, UnaryOperator<Term> f) {
    return fun == this.fun && arg == this.arg ? this : new AppTerm(fun, arg).make(f);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, fun), f.apply(0, arg), term -> f.apply(0, term));
  }

  public static @Closed @NotNull Term make(@Closed @NotNull Term f, @Closed @NotNull Term a) { return new AppTerm(f, a).make(); }
  public static @Closed @NotNull Term make(@Closed @NotNull Term f, @NotNull SeqView<@Closed Term> args) {
    for (@Closed var arg : args) f = make(f, arg);
    return f;
  }

  @Override public @NotNull Term make(@NotNull UnaryOperator<Term> mapper) {
    return switch (fun) {
      case LamTerm(var closure) -> mapper.apply(closure.apply(arg));
      case MetaCall(var ref, var args) -> new MetaCall(ref, args.appended(arg));
      default -> this;
    };
  }

  public record UnApp(@NotNull ImmutableSeq<Term> args, @NotNull Term fun) { }
  public static @NotNull UnApp unapp(@NotNull Term maybeApp) {
    var args = MutableList.<Term>create();
    while (maybeApp instanceof AppTerm(var f, var a)) {
      maybeApp = f;
      args.append(a);
    }
    return new UnApp(args.reversed(), maybeApp);
  }
}
