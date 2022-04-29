// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.core.def.Def;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

import static org.aya.core.repr.CodeShape.CtorShape;
import static org.aya.core.repr.CodeShape.DataShape;

/**
 * @author kiva
 */
public sealed interface AyaShape {
  @NotNull CodeShape codeShape();
  @NotNull Term transform(@NotNull Term term, @NotNull Term type);

  @NotNull CodeShape DATA_NAT = new DataShape(ImmutableSeq.empty(), ImmutableSeq.of(
    new CtorShape(ImmutableSeq.empty()),
    new CtorShape(ImmutableSeq.of(CodeShape.ParamShape.ex(new CodeShape.TermShape.Call(0))))
  ));

  @NotNull AyaShape NAT_SHAPE = new AyaIntLitShape();
  @NotNull ImmutableSeq<AyaShape> LITERAL_SHAPES = ImmutableSeq.of(NAT_SHAPE);

  record AyaIntLitShape() implements AyaShape {
    @Override public @NotNull CodeShape codeShape() {
      return DATA_NAT;
    }

    @Override public @NotNull Term transform(@NotNull Term term, @NotNull Term type) {
      return term;
    }
  }

  record Factory(@NotNull MutableMap<Def, MutableList<AyaShape>> discovered) {
    public Factory() {
      this(MutableLinkedHashMap.of());
    }
  }
}
