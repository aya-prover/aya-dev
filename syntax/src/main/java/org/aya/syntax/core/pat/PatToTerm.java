// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.pat;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.ConCall;
import org.aya.syntax.core.term.call.ConCallLike;
import org.aya.syntax.core.term.xtt.DimTerm;
import org.aya.syntax.ref.LocalCtx;
import org.aya.util.Pair;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public interface PatToTerm {
  static @NotNull Term visit(@NotNull Pat pat) {
    return new Unary(_ -> { }).apply(pat);
  }
  record Unary(@NotNull Consumer<Pat.Bind> freshCallback) implements Function<Pat, Term> {
    @Override public Term apply(Pat pat) {
      return switch (pat) {
        // We expect this to be never used
        case Pat.Absurd _ -> SortTerm.Type0;
        case Pat.Bind bind -> {
          freshCallback.accept(bind);
          yield new FreeTerm(bind.bind());
        }
        case Pat.Con con -> new ConCall(conHead(con), con.args().map(this));
        case Pat.Tuple tuple -> new TupTerm(tuple.elements().map(this));
        case Pat.Meta meta -> new MetaPatTerm(meta);
        case Pat.ShapedInt si -> si.toTerm();
      };
    }
  }
  private static ConCallLike.@NotNull Head conHead(Pat.Con con) {
    return new ConCallLike.Head(con.ref(), 0, con.data().args());
  }

  record Binary(@NotNull LocalCtx ctx, @NotNull Unary unary) implements BiFunction<Pat, Pat, Term> {
    public Binary(@NotNull LocalCtx ctx) {
      this(ctx, new Unary(bind -> ctx.put(bind.bind(), bind.type())));
    }
    public @NotNull ImmutableSeq<Term> list(@NotNull ImmutableSeq<Pat> lhs, @NotNull ImmutableSeq<Pat> rhs) {
      return lhs.zip(rhs, this);
    }

    public @NotNull Term apply(@NotNull Pat l, @NotNull Pat r) {
      return switch (new Pair<>(l, r)) {
        case Pair(Pat.Bind _, var rhs) -> unary.apply(rhs);
        case Pair(var lhs, Pat.Bind _) -> unary.apply(lhs);
        // It must be the case that lhs.ref == rhs.ref
        case Pair(Pat.Con lhs, Pat.Con rhs) -> new ConCall(conHead(lhs), list(lhs.args(), rhs.args()));
        case Pair(Pat.ShapedInt lhs, Pat.Con rhs) -> apply(lhs.constructorForm(), rhs);
        case Pair(Pat.Con lhs, Pat.ShapedInt rhs) -> apply(lhs, rhs.constructorForm());
        case Pair(Pat.ShapedInt lhs, Pat.ShapedInt _) -> lhs.toTerm();
        case Pair(Pat.Tuple lhs, Pat.Tuple rhs) -> new TupTerm(list(lhs.elements(), rhs.elements()));
        default -> Panic.unreachable();
      };
    }
  }

  record Monadic(@NotNull LocalCtx ctx) implements Function<Pat, ImmutableSeq<Term>> {
    /**
     * Vertically two possibilities:
     * [ [0]
     * , [1] ]
     */
    private static final @NotNull ImmutableSeq<ImmutableSeq<Term>> BOUNDARIES = ImmutableSeq.of(
      ImmutableSeq.of(DimTerm.I0), ImmutableSeq.of(DimTerm.I1)
    );
    public @NotNull ImmutableSeq<ImmutableSeq<Term>> list(@NotNull SeqView<Pat> pats) {
      return list(pats, ImmutableSeq.of(ImmutableSeq.empty()));
    }
    private @NotNull ImmutableSeq<ImmutableSeq<Term>> list(@NotNull SeqView<Pat> pats, @NotNull ImmutableSeq<ImmutableSeq<Term>> base) {
      if (pats.isEmpty()) return base;
      // We have non-deterministically one of these head
      var headND = apply(pats.getFirst());
      // We have non-deterministically one of these tails
      var tailND = list(pats.drop(1), base);
      return tailND.flatMap(ogList -> headND.map(ogList::prepended));
    }

    @Override public ImmutableSeq<Term> apply(Pat pat) {
      return switch (pat) {
        case Pat.Absurd _, Pat.Meta _ -> Panic.unreachable();
        case Pat.ShapedInt si -> ImmutableSeq.of(si.toTerm());
        case Pat.Bind bind -> {
          ctx.put(bind.bind(), bind.type());
          yield ImmutableSeq.of(new FreeTerm(bind.bind()));
        }
        case Pat.Con con when con.ref().hasEq() ->
          list(con.args().view().dropLast(1), BOUNDARIES)
            .map(args -> new ConCall(conHead(con), args));
        case Pat.Con con -> list(con.args().view())
          .map(args -> new ConCall(conHead(con), args));
        case Pat.Tuple tuple -> list(tuple.elements().view())
          .map(args -> new TupTerm(args));
      };
    }
  }
}
