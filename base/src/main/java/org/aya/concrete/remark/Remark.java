// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.remark;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.GenericAyaParser;
import org.aya.concrete.stmt.Stmt;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.Context;
import org.aya.tyck.order.TyckOrder;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public final class Remark implements Stmt {
  public final @Nullable Literate literate;
  public final @NotNull String raw;
  public final @NotNull SourcePos sourcePos;
  public @Nullable Context ctx = null;

  private Remark(@Nullable Literate literate, @NotNull String raw, @NotNull SourcePos sourcePos) {
    this.literate = literate;
    this.raw = raw;
    this.sourcePos = sourcePos;
  }

  public static @NotNull Remark make(@NotNull String raw, @NotNull SourcePos pos, @NotNull GenericAyaParser ayaParser) {
    throw new UnsupportedOperationException("TODO");
    // return new Remark(literate, raw, pos);
  }

  @Override public @NotNull Accessibility accessibility() {
    return Accessibility.Private;
  }

  public @NotNull SourcePos sourcePos() {
    return sourcePos;
  }

  public @NotNull ImmutableSeq<TyckOrder> doResolve(@NotNull ResolveInfo info) {
    if (literate == null) return ImmutableSeq.empty();
    assert ctx != null : "Be sure to call the shallow resolver before resolving";
    // TODO[CHECK]: resolve literate
    // return literate.resolve(info, ctx);
    return ImmutableSeq.empty();
  }

  /** It's always downstream (cannot be imported), so always need to be checked. */
  @Override public boolean needTyck(@NotNull ImmutableSeq<String> currentMod) {
    return true;
  }
}
