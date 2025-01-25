// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.pat;

import java.util.function.Function;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.ConCall;
import org.aya.syntax.core.term.xtt.DimTerm;
import org.aya.syntax.ref.LocalCtx;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

public interface PatToTerm {
  static @NotNull Term visit(@NotNull Pat pat) {
    return switch (pat) {
      case Pat.Misc misc -> switch (misc) {
        // We expect this to be never used, but this needs to not panic because
        // absurd clauses need to finish type checking
        case Absurd -> SortTerm.Type0;
        // case UntypedBind -> Panic.unreachable();
      };
      case Pat.Bind bind -> new FreeTerm(bind.bind());
      case Pat.Con con -> new ConCall(con.head(), con.args().map(PatToTerm::visit));
      case Pat.Tuple(var l, var r) -> new TupTerm(visit(l), visit(r));
      case Pat.Meta meta -> new MetaPatTerm(meta);
      case Pat.ShapedInt si -> si.toTerm();
    };
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
        case Pat.Misc _, Pat.Meta _ -> Panic.unreachable();
        case Pat.ShapedInt si -> ImmutableSeq.of(si.toTerm());
        case Pat.Bind bind -> {
          ctx.put(bind.bind(), bind.type());
          yield ImmutableSeq.of(new FreeTerm(bind.bind()));
        }
        case Pat.Con con when con.ref().hasEq() -> list(con.args().view().dropLast(1), BOUNDARIES)
          .map(args -> {
            return new ConCall(con.head(), args);
          });
        case Pat.Con con -> list(con.args().view())
          .map(args -> {
            return new ConCall(con.head(), args);
          });
        case Pat.Tuple(var l, var r) -> list(Seq.of(l, r).view())
          .map(args -> new TupTerm(args.get(0), args.get(1)));
      };
    }
  }
}
