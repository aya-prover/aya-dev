// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.value.Var;
import org.jetbrains.annotations.NotNull;

public interface Selector {
  sealed interface Result<T> {}

  /** The old one is better, The new one is useless. */
  record Useless<A>(@NotNull A better) implements Result<A> {}

  /** The new one is better, and it beats all {@link #junks}, but it still worse than {@link #betters} */
  record Evolve<A>(@NotNull SeqView<A> junks, @NotNull SeqView<A> betters) implements Result<A> {}

  static <A extends Candidate<A>> @NotNull Result<A> select(@NotNull A a, @NotNull SeqView<A> had) {
    if (had.isEmpty()) return new Evolve<>(SeqView.empty(), SeqView.empty());
    var b = had.first();
    var bs = had.drop(1);
    return switch (a.compare(b)) {
      // `a` is not strictly better than b, dropping `a`
      case Eq, Ge, Gt -> new Useless<>(b);
      // `a` is strictly better than b, dropping `b`
      case Lt, Le -> switch (select(a, bs)) {
        case Evolve<A> e -> new Evolve<>(e.junks.appended(b), e.betters);
        case Useless<A> u -> u;
      };
      // cannot compare, keep both
      case Unk -> switch (select(a, bs)) {
        case Evolve<A> e -> new Evolve<>(e.junks, e.betters.appended(b));
        case Useless<A> u -> u;
      };
    };
  }

  static <A extends Candidate<A>> @NotNull Tuple2<ImmutableSeq<A>, ImmutableSeq<A>>
  select(@NotNull ImmutableSeq<A> new_, @NotNull ImmutableSeq<A> old_) {
    var old = new Var<>(old_);
    var winners = MutableList.<A>create();
    new_.forEach(a -> {
      switch (select(a, old.value.view())) {
        case Evolve<A> e -> {
          winners.append(a);
          // dropping elements worse than `a`
          old.value = e.betters.toImmutableSeq();
        }
        case Useless<A> u -> {}
      }
    });
    return Tuple.of(winners.toImmutableSeq(), old.value);
  }

  enum PartialOrd {
    Lt, Le, Eq, Ge, Gt, Unk;

    public static @NotNull Selector.PartialOrd compareBool(boolean l, boolean r) {
      if (!l && r) return Selector.PartialOrd.Lt;
      if (l && !r) return Selector.PartialOrd.Gt;
      return Selector.PartialOrd.Eq;
    }

    public static @NotNull Selector.PartialOrd compareInt(int l, int r) {
      if (l < r) return Selector.PartialOrd.Lt;
      if (l > r) return Selector.PartialOrd.Gt;
      return Selector.PartialOrd.Eq;
    }

    public @NotNull PartialOrd or(@NotNull PartialOrd rhs) {
      if (this == Unk) return rhs;

      if (this == Lt && rhs == Lt) return Lt;
      if (this == Lt && rhs == Le) return Le;
      if (this == Lt && rhs == Eq) return Le;

      if (this == Le && rhs == Lt) return Le;
      if (this == Le && rhs == Le) return Le;
      if (this == Le && rhs == Eq) return Le;

      if (this == Eq && rhs == Lt) return Le;
      if (this == Eq && rhs == Le) return Le;
      if (this == Eq && rhs == Eq) return Eq;
      if (this == Eq && rhs == Ge) return Ge;
      if (this == Eq && rhs == Gt) return Ge;

      if (this == Ge && rhs == Eq) return Ge;
      if (this == Ge && rhs == Ge) return Ge;
      if (this == Ge && rhs == Gt) return Ge;

      if (this == Gt && rhs == Eq) return Ge;
      if (this == Gt && rhs == Ge) return Ge;
      if (this == Gt && rhs == Gt) return Gt;

      return Unk;
    }

    public @NotNull PartialOrd and(@NotNull PartialOrd rhs) {
      if (this == Unk) return Unk;
      if (this == Eq) return rhs;

      if (this == Lt && rhs == Lt) return Lt;
      if (this == Lt && rhs == Le) return Lt;
      if (this == Lt && rhs == Eq) return Lt;

      if (this == Le && rhs == Lt) return Lt;
      if (this == Le && rhs == Le) return Le;
      if (this == Le && rhs == Eq) return Le;

      if (this == Ge && rhs == Eq) return Ge;
      if (this == Ge && rhs == Ge) return Ge;
      if (this == Ge && rhs == Gt) return Gt;

      if (this == Gt && rhs == Eq) return Gt;
      if (this == Gt && rhs == Ge) return Gt;
      if (this == Gt && rhs == Gt) return Gt;

      return Unk;
    }
  }

  interface Candidate<T> {
    /** Compare elements by their decrease amount. */
    @NotNull Selector.PartialOrd compare(@NotNull T other);
  }
}
