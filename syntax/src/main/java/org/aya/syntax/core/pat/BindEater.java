// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.pat;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.value.MutableValue;
import org.aya.syntax.core.term.Term;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * We eat bindings, now there are only holes.
 */
public record BindEater(
  @NotNull ImmutableSeq<Term> formerArgs,
  @NotNull MutableList<Term> mouth
) implements UnaryOperator<Pat> {
  private @NotNull SeqView<Term> inst() {
    return formerArgs.view().appendedAll(mouth);
  }

  @Override public @NotNull Pat apply(@NotNull Pat pat) {
    return switch (pat) {
      // {pat} is supposed to be a tycked (not still tycking) pattern,
      // which should not contain meta pattern
      case Pat.Meta _ -> throw new Panic("I don't like holes :(");
      case Pat.Bind bind -> {
        var realType = bind.type().instTele(inst());
        var meta = new Pat.Meta(MutableValue.create(), bind.bind().name(), realType, bind.bind().definition());
        // yummy yummy
        mouth.append(PatToTerm.visit(meta));
        yield meta;
      }
      case Pat.Con con -> {
        var realType = con.head().instantiateTele(inst());
        yield con.update(con.args().map(this), realType);
      }

      default -> pat.descentPat(this);
    };
  }
}
