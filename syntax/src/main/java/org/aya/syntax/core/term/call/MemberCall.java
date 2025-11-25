// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.call;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.def.MemberDefLike;
import org.aya.syntax.core.term.AppTerm;
import org.aya.syntax.core.term.ClassCastTerm;
import org.aya.syntax.core.term.NewTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record MemberCall(
  @NotNull Term of,
  @Override @NotNull MemberDefLike ref,
  @Override int ulift,
  @NotNull ImmutableSeq<@NotNull Term> projArgs
) implements Callable.Tele, BetaRedex {
  private Term update(Term clazz, ImmutableSeq<Term> newArgs, UnaryOperator<Term> f) {
    return clazz == of && newArgs.sameElements(projArgs, true) ? this
      : new MemberCall(clazz, ref, ulift, newArgs).make(f);
  }

  @Override public @NotNull ImmutableSeq<@NotNull Term> args() {
    return projArgs.prepended(of);
  }
  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, of), Callable.descent(projArgs, f), term -> f.apply(0, term));
  }

  public static @NotNull Term make(
    @NotNull ClassCall typeOfOf, @NotNull Term of,
    @NotNull MemberDefLike ref, int ulift,
    @NotNull ImmutableSeq<@NotNull Term> args
  ) {
    var impl = typeOfOf.get(ref);
    if (impl != null) return impl.apply(of);
    return new MemberCall(of, ref, ulift, args).make();
  }

  /**
   * Trying to normalize this {@link MemberCall}:
   * <ul>
   *   <li>If it calls on a {@link NewTerm}, we just pick the corresponding term.</li>
   *   <li>
   *     If it calls on a {@link ClassCastTerm}, we will try to obtain the restriction information from it (see {@link ClassCastTerm#get(MemberDefLike)}).
   *     If failed, we look inside. Note that nested {@link ClassCastTerm} is possible,
   *     we also return {@code mostInnerTerm .someField} when we are unable to normalize it anymore, it looks like we normalized
   *     the arguments of a function call but we still unable to unfold it.
   *   </li>
   *   <li>Otherwise, we just return the {@link MemberCall} itself</li>
   * </ul>
   */
  @Override public @NotNull Term make(@NotNull UnaryOperator<Term> mapper) {
    // `this` must be closed by assumption
    @Closed var self = this;

    return switch (self.of()) {
      case NewTerm neu -> {
        var impl = neu.inner().get(ref);
        assert impl != null; // NewTerm is always fully applied
        yield mapper.apply(AppTerm.make(impl.apply(neu), self.projArgs.view()));
      }
      case ClassCastTerm cast -> {
        var impl = cast.get(ref);
        if (impl != null) yield mapper.apply(AppTerm.make(impl.apply(cast), self.projArgs.view()));
        // no impl, try inner
        assert !(cast.subterm() instanceof ClassCastTerm) : "eliminated by ClassCastTerm#make";
        yield update(cast.subterm(), projArgs, mapper);
      }
      default -> this;
    };
  }
}
