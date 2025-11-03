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
import org.aya.syntax.core.term.marker.BindingIntro;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record LetTerm(@NotNull Term definedAs, @NotNull Closure body) implements Term, BetaRedex, BindingIntro {
  public @NotNull LetTerm update(@NotNull Term definedAs, @NotNull Closure body) {
    return definedAs == definedAs() && body == body()
      ? this
      : new LetTerm(definedAs, body);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, definedAs), body.descent(f));
  }

  /// @apiNote this [LetTerm] must be [Closed]
  @Override public @Closed @NotNull Term make(@NotNull UnaryOperator<@Closed Term> mapper) {
    @Closed LetTerm self = this;
    // [body] and [definedAs] are closed since [this] is [Closed], thus [body.apply(definedAs)] is also closed.
    return mapper.apply(body.apply(self.definedAs()));
  }

  public static @Closed @NotNull Term makeAll(@Closed @NotNull Term term) {
    if (term instanceof LetTerm l) l.make(LetTerm::makeAll);
    return term;
  }

  public record Unlet(@NotNull ImmutableSeq<LetFreeTerm> definedAs, @NotNull Term body) {
  }

  /// Extract the innermost body of `this` [LetTerm], be aware that the returned [Term] is [Bound].
  public static @NotNull @Bound Term unletBody(Term term) {
    while (term instanceof LetTerm let) term = let.body().unwrap();
    return term;
  }

  /// @apiNote `this` must be [Closed]
  public @NotNull Unlet unlet(@NotNull Renamer nameGen) {
    var definedAs = FreezableMutableList.<LetFreeTerm>create();
    @Closed Term let = this;

    while (let instanceof LetTerm(var term, var remain)) {
      if (term instanceof FreeTerm free) {
        let = remain.apply(free);
        continue;
      }

      @Closed LetFreeTerm bind = new LetFreeTerm(nameGen.bindName(term), Jdg.TypeMissing.of(term));
      var freeBody = remain.apply(bind);

      definedAs.append(bind);
      let = freeBody;
    }

    return new Unlet(definedAs.toSeq(), let);
  }

  public static @Closed @NotNull Term bind(@NotNull LetFreeTerm bind, @Closed @NotNull Term body) {
    var name = bind.name();
    var definedAs = bind.definedAs().wellTyped();
    var boundBody = body.bind(name);
    if (boundBody.body() == body) return body;
    return new LetTerm(definedAs, boundBody);
  }
}
