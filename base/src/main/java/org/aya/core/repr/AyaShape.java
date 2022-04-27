// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.repr;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import kala.tuple.Tuple;
import org.aya.core.def.Def;
import org.jetbrains.annotations.NotNull;

import static org.aya.core.repr.CodeShape.CtorShape;
import static org.aya.core.repr.CodeShape.DataShape;

/**
 * @author kiva
 */
public sealed interface AyaShape {
  @NotNull CodeShape codeShape();

  @NotNull AyaShape NAT_SHAPE = new AyaIntLitShape();
  @NotNull ImmutableSeq<AyaShape> LITERAL_SHAPES = ImmutableSeq.of(NAT_SHAPE);

  record AyaIntLitShape() implements AyaShape {
    public static final @NotNull CodeShape DATA_NAT = new DataShape(ImmutableSeq.empty(), ImmutableSeq.of(
      new CtorShape(ImmutableSeq.empty()),
      new CtorShape(ImmutableSeq.of(CodeShape.ParamShape.ex(new CodeShape.TermShape.Call(0))))
    ));

    @Override public @NotNull CodeShape codeShape() {
      return DATA_NAT;
    }
  }

  record Factory(@NotNull MutableMap<Def, AyaShape> discovered) {
    public Factory() {
      this(MutableLinkedHashMap.of());
    }

    public @NotNull ImmutableSeq<Def> findImpl(@NotNull AyaShape shape) {
      return discovered.view().map(Tuple::of)
        .filter(t -> t._2 == shape)
        .map(t -> t._1)
        .toImmutableSeq();
    }

    public @NotNull Option<AyaShape> find(@NotNull Def def) {
      return discovered.getOption(def);
    }

    public void bonjour(@NotNull Def def, @NotNull AyaShape shape) {
      // TODO[literal]: what if a def has multiple shapes?
      discovered.put(def, shape);
    }
  }
}
