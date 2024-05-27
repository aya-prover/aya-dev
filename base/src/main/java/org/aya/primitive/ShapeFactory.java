// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.primitive;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableMap;
import kala.control.Option;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.TyckAnyDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.repr.AyaShape;
import org.aya.syntax.core.repr.ShapeRecognition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShapeFactory {
  public @NotNull MutableMap<AnyDef, ShapeRecognition> discovered = MutableLinkedHashMap.of();

  public @NotNull ImmutableSeq<AyaShape.FindImpl> findImpl(@NotNull AyaShape shape) {
    return discovered.view()
      .map(AyaShape.FindImpl::new)
      .filter(t -> t.recog().shape() == shape)
      .toImmutableSeq();
  }

  public @NotNull Option<ShapeRecognition> find(@Nullable AnyDef def) {
    if (def == null) return Option.none();
    return discovered.getOption(def);
  }

  /** @implNote assumption: defs can have only one shape */
  public void bonjour(@NotNull TyckDef def, @NotNull ShapeRecognition shape) {
    discovered.put(TyckAnyDef.make(def), shape);
  }

  /** Discovery of shaped literals */
  public void bonjour(@NotNull TyckDef def) {
    for (var shape : AyaShape.values()) {
      new ShapeMatcher(ImmutableMap.from(discovered)).match(shape, def)
        .ifDefined(recog -> bonjour(def, recog));
    }
  }

  public void importAll(@NotNull ShapeFactory other) { discovered.putAll(other.discovered); }
}
