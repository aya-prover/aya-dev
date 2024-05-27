// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.function.IndexedFunction;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.jetbrains.annotations.NotNull;

public record AppTerm(@NotNull Term fun, @NotNull Term arg) implements BetaRedex {
  public @NotNull Term update(@NotNull Term fun, @NotNull Term arg) {
    return fun == this.fun && arg == this.arg ? this : new AppTerm(fun, arg).make();
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, fun), f.apply(0, arg));
  }

  public static @NotNull Term make(@NotNull Term f, @NotNull Term a) { return new AppTerm(f, a).make(); }
  public static @NotNull Term make(@NotNull Term f, @NotNull SeqView<Term> args) {
    for (var arg : args) f = make(f, arg);
    return f;
  }

  @Override public @NotNull Term make() {
    return switch (fun) {
      case LamTerm(var closure) -> closure.apply(arg);
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
