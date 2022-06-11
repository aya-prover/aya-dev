// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import kala.value.Ref;
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
  @NotNull Ref<@Nullable Context> context,
  @NotNull ImmutableSeq<QualifiedID> loosers,
  @NotNull ImmutableSeq<QualifiedID> tighters,
  @ForLSP @NotNull Ref<ImmutableSeq<DefVar<?, ?>>> resolvedLoosers,
  @ForLSP @NotNull Ref<ImmutableSeq<DefVar<?, ?>>> resolvedTighters
) {
  public static final @NotNull BindBlock EMPTY = new BindBlock(SourcePos.NONE, new Ref<>(), ImmutableSeq.empty(), ImmutableSeq.empty(), new Ref<>(), new Ref<>());
}
