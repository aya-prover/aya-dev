// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.mutable.MutableList;
import kala.value.MutableValue;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.pat.PatToTerm;
import org.aya.syntax.core.term.Term;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * We eat bindings, now there are only holes.
 */
public record BindEater(@NotNull MutableList<Term> mouth) implements UnaryOperator<Pat> {
  @Override public @NotNull Pat apply(@NotNull Pat pat) {
    return switch (pat) {
      // {pat} is supposed to be a tycked (not still tycking) pattern,
      // which should not contain meta pattern
      case Pat.Meta _ -> throw new Panic("I don't like holes :(");
      case Pat.Bind bind -> {
        var meta = new Pat.Meta(MutableValue.create(), bind.bind().name(), bind.type(), bind.bind().definition());
        // yummy yummy
        mouth.append(PatToTerm.visit(meta));
        yield meta;
      }

      default -> pat.descent(this, UnaryOperator.identity());
    };
  }
}
