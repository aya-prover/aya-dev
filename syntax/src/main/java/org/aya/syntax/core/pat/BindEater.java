// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.pat;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.value.MutableValue;
import org.aya.syntax.core.annotation.Bound;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.Term;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * We eat bindings, now there are only holes.
 */
public record BindEater(
  @NotNull ImmutableSeq<@Closed Term> formerArgs,
  @NotNull MutableList<@Closed Term> mouth
) implements UnaryOperator<Pat> {
  private @NotNull SeqView<@Closed Term> inst() {
    return formerArgs.view().appendedAll(mouth);
  }

  @Override public @Closed @NotNull Pat apply(@Bound @NotNull Pat pat) {
    return switch (pat) {
      // {pat} is supposed to be a tycked (not still tycking) pattern,
      // which should not contain meta pattern
      case Pat.Meta _ -> throw new Panic("I don't like holes :(");
      case Pat.Bind bind -> {
        // since pat is well-scoped
        @Closed var realType = bind.type().instTele(inst());
        // Closed cause realType is Closed
        @Closed var meta = new Pat.Meta(MutableValue.create(), bind.bind().name(), realType, bind.bind().definition());
        // yummy yummy
        mouth.append(PatToTerm.visit(meta));
        yield meta;
      }
      case Pat.Con con -> {
        @Closed var realType = con.head().instantiateTele(inst());
        yield con.update(con.args().map(this), realType);
      }

      // We don't need to handle this case, even `Pat.ShapedInt` contains a `DataCall`,
      // cause we know the `DataCall` has 0 arguments, thus it is always Closed
      // TODO: luckily we don't have Pat.List for now
      case Pat.ShapedInt _ -> pat.descentPat(this);

      default -> pat.descentPat(this);
    };
  }
}
