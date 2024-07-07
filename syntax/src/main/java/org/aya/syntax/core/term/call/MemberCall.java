// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.core.def.MemberDefLike;
import org.aya.syntax.core.term.ClassCastTerm;
import org.aya.syntax.core.term.NewTerm;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public record MemberCall(
  @NotNull Term of,
  @Override @NotNull MemberDefLike ref,
  @Override int ulift,
  @Override @NotNull ImmutableSeq<@NotNull Term> args
) implements Callable.Tele {
  private MemberCall update(Term clazz, ImmutableSeq<Term> newArgs) {
    return clazz == of && newArgs.sameElements(args, true) ? this
      : new MemberCall(clazz, ref, ulift, newArgs);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, of), Callable.descent(args, f));
  }

  public static @NotNull Term make(
    @NotNull ClassCall typeOfOf,
    @NotNull Term of,
    @NotNull MemberDefLike ref,
    int ulift,
    @NotNull ImmutableSeq<@NotNull Term> args
  ) {
    var impl = typeOfOf.get(ref);
    if (impl != null) return impl.apply(of);
    return make(new MemberCall(of, ref, ulift, args));
  }

  public static @NotNull Term make(@NotNull MemberCall call) {
    return switch (call.of()) {
      case NewTerm neu -> {
        var impl = neu.inner().get(call.ref);
        assert impl != null;    // NewTerm is always fully applied
        yield impl.apply(neu);
      }
      case ClassCastTerm cast -> {
        var impl = cast.get(call.ref);
        if (impl != null) yield impl.apply(cast);
        // no impl, try inner
        yield call.update(cast.subterm(), call.args);
      }
      default -> call;
    };
  }
}
