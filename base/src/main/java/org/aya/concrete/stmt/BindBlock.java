// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import kala.value.MutableValue;
import org.aya.ref.DefVar;
import org.aya.resolve.context.Context;
import org.aya.util.ForLSP;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kiva
 */
public record BindBlock(
  @Override @NotNull SourcePos sourcePos,
  @NotNull MutableValue<@Nullable Context> context,
  @NotNull ImmutableSeq<QualifiedID> loosers,
  @NotNull ImmutableSeq<QualifiedID> tighters,
  @ForLSP @NotNull MutableValue<ImmutableSeq<DefVar<?, ?>>> resolvedLoosers,
  @ForLSP @NotNull MutableValue<ImmutableSeq<DefVar<?, ?>>> resolvedTighters
) {
  public static final @NotNull BindBlock EMPTY = new BindBlock(SourcePos.NONE, MutableValue.create(), ImmutableSeq.empty(), ImmutableSeq.empty(), MutableValue.create(), MutableValue.create());
}
