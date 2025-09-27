// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.FreezableMutableList;
import kala.function.IndexedFunction;
import org.aya.generic.Renamer;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.Jdg;
import org.aya.syntax.core.annotation.Bound;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record LetTerm(@NotNull Term definedAs, @NotNull Closure body) implements Term, BetaRedex {
  public @NotNull LetTerm update(@NotNull Term definedAs, @NotNull Closure body) {
    return definedAs == definedAs() && body == body()
      ? this
      : new LetTerm(definedAs, body);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, definedAs), body.descent(f));
  }

  @Override public @NotNull Term make(@NotNull UnaryOperator<Term> mapper) {
    return mapper.apply(body.apply(definedAs));
  }

  public static @NotNull Term makeAll(@NotNull Term term) {
    if (term instanceof LetTerm l) l.make(LetTerm::makeAll);
    return term;
  }

  public record Unlet(@NotNull ImmutableSeq<LetFreeTerm> definedAs, @NotNull Term body) {
  }

  public @NotNull Unlet unlet(@NotNull Renamer nameGen) {
    var definedAs = FreezableMutableList.<LetFreeTerm>create();
    Term let = this;

    while (let instanceof LetTerm(var term, var remain)) {
      if (term instanceof FreeTerm free) {
        let = remain.apply(free);
        continue;
      }
      var bind = new LetFreeTerm(nameGen.bindName(term), new Jdg.TypeMissing(term));
      var freeBody = remain.apply(bind);

      definedAs.append(bind);
      let = freeBody;
    }

    return new Unlet(definedAs.toSeq(), let);
  }

  public static @NotNull @Closed Term bind(@NotNull LetFreeTerm bind, @NotNull @Closed Term body) {
    var name = bind.name();
    var definedAs = bind.definedAs().wellTyped();
    var boundBody = body.bind(name);
    if (boundBody.body() == body) return body;
    return new LetTerm(definedAs, boundBody);
  }
}
