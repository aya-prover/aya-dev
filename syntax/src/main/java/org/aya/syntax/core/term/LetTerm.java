// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.FreezableMutableList;
import kala.function.IndexedFunction;
import org.aya.generic.Renamer;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.aya.syntax.ref.GenerateKind;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

public record LetTerm(@NotNull Term definedAs, @NotNull Closure body) implements Term, BetaRedex {
  public @NotNull LetTerm update(@NotNull Term definedAs, @NotNull Closure body) {
    return definedAs == definedAs() && body == body()
      ? this
      : new LetTerm(definedAs, body);
  }

  @Override
  public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, definedAs), body.descent(f));
  }

  @Override
  public @NotNull Term make(@NotNull UnaryOperator<Term> mapper) {
    return mapper.apply(body.apply(definedAs));
  }

  public record Unlet(@NotNull ImmutableSeq<LetFreeTerm> definedAs, @NotNull Term body) {
  }

  public @NotNull Unlet unlet(@NotNull Renamer nameGen) {
    var definedAs = FreezableMutableList.<LetFreeTerm>create();
    Term let = this;

    while (let instanceof LetTerm(var term, var body)) {
      var bind = new LetFreeTerm(nameGen.bindName(term), term);
      var freeBody = body.apply(bind);

      definedAs.append(bind);
      let = freeBody;
    }

    return new Unlet(definedAs.toSeq(), let);
  }
}
