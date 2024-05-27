// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import kala.value.MutableValue;
import org.aya.syntax.ref.DefVar;
import org.aya.util.ForLSP;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

public record BindBlock(
  @Override @NotNull SourcePos sourcePos,
  @NotNull ImmutableSeq<QualifiedID> loosers,
  @NotNull ImmutableSeq<QualifiedID> tighters,
  // TODO: make AnyVar
  @ForLSP @NotNull MutableValue<ImmutableSeq<DefVar<?, ?>>> resolvedLoosers,
  @ForLSP @NotNull MutableValue<ImmutableSeq<DefVar<?, ?>>> resolvedTighters
) {
  public static final @NotNull BindBlock EMPTY = new BindBlock(SourcePos.NONE,
    ImmutableSeq.empty(), ImmutableSeq.empty(), MutableValue.create(), MutableValue.create());
}
