// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.ser;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.stmt.BindBlock;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.ref.QName;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public record SerBind(@NotNull ImmutableSeq<QName> loosers,
                      @NotNull ImmutableSeq<QName> tighters) implements Serializable {
  public static final SerBind EMPTY = new SerBind(ImmutableSeq.empty(), ImmutableSeq.empty());

  public static @NotNull SerBind from(@NotNull BindBlock bindBlock) {
    if (bindBlock == BindBlock.EMPTY) return SerBind.EMPTY;
    var loosers = bindBlock.resolvedLoosers().get().map(x -> AnyDef.fromVar(x).qualifiedName());
    var tighters = bindBlock.resolvedTighters().get().map(x -> AnyDef.fromVar(x).qualifiedName());
    return new SerBind(loosers, tighters);
  }
}
