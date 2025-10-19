// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.normalize;

import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Term;
import org.aya.tyck.ctx.LocalLet;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/// This implements [FreeTerm] substitution. The substitution object is represented using a
/// [LocalLet] for convenience -- for the functionality we only need [LocalLet#contains] and [LocalLet#get].
public record LetReplacer(@NotNull LocalLet let) implements UnaryOperator<Term> {
  @Override public Term apply(Term term) {
    return switch (term) {
      case FreeTerm(var name) when let.contains(name) -> apply(let.getTerm(name));
      default -> term.descent(this);
    };
  }
}
