// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
import org.aya.core.visitor.AyaRestrSimplifier;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Partial.Const;
import org.aya.guest0x0.cubical.Partial.Split;
import org.aya.guest0x0.cubical.Restr;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * Partial elements.
 *
 * @implNote I am unsure if this is stable as of our assumptions. Surely
 * a split partial may become a const partial, is that stable?
 */
public record PartialTerm(@NotNull Partial<Term> partial, @NotNull Term rhsType) implements Term {
  public static @NotNull Partial<Term> merge(@NotNull Seq<Partial<Term>> partials) {
    // Just a mild guess without scientific rationale
    var list = MutableArrayList.<Restr.Side<Term>>create(partials.size() * 2);
    for (var partial : partials) {
      switch (partial) {
        case Split<Term> s -> list.appendAll(s.clauses());
        case Const<Term> c -> {
          return c;
        }
      }
    }
    return AyaRestrSimplifier.INSTANCE.mapPartial(
      new Split<>(list.toImmutableArray()),
      UnaryOperator.identity());
  }

  public static final @NotNull Partial.Split<Term> DUMMY_SPLIT = new Split<>(ImmutableSeq.empty());

  public static Partial<Term> amendTerms(Partial<Term> p, UnaryOperator<Term> applyDimsTo) {
    return switch (p) {
      case Partial.Split<Term> s -> new Split<>(s.clauses().map(c ->
        new Restr.Side<>(c.cof(), applyDimsTo.apply(c.u()))));
      case Partial.Const<Term> c -> new Const<>(applyDimsTo.apply(c.u()));
    };
  }
}
