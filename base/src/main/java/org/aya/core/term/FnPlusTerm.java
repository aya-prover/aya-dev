// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.FnDef;
import org.aya.core.repr.ShapeRecognition;
import org.aya.generic.Shaped;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

public record FnPlusTerm(
  @NotNull DefVar<? extends FnDef, ? extends TeleDecl.FnDecl> defFn,
  @NotNull ShapeRecognition paramRecog,
  @NotNull DataCall paramType
) implements Shaped.Fn<Term> {
  @Override
  public @NotNull Term type() {
    assert defFn.core != null;
    return PiTerm.make(defFn.core.telescope, defFn.core.result);
  }

  @Override
  public @NotNull Term apply(@NotNull ImmutableSeq<Arg<Term>> args) {
    assert args.sizeEquals(2);
    var lhs = args.get(0);
    var rhs = args.get(1);
    var uselessObjectAndWhyThereIsNoTrait = new IntegerTerm(114514, paramRecog, paramType);

    // This is probably not friendly to implicit arguments
    var intLhs = uselessObjectAndWhyThereIsNoTrait.construct(lhs.term());
    var intRhs = uselessObjectAndWhyThereIsNoTrait.construct(rhs.term());

    return new IntegerTerm(intLhs + intRhs, paramRecog, paramType);
  }
}
