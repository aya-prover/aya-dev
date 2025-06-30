// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend;

import kala.collection.immutable.ImmutableSeq;
import org.aya.resolve.ResolveInfo;
import org.aya.syntax.core.def.TyckDef;
import org.jetbrains.annotations.NotNull;

public class IRCompiler {

  private final @NotNull ResolveInfo resolveInfo;
  private final @NotNull ImmutableSeq<TyckDef> defs;

  public IRCompiler(@NotNull ResolveInfo resolveInfo, @NotNull ImmutableSeq<TyckDef> defs) {
    this.resolveInfo = resolveInfo;
    this.defs = defs;
  }

  public void build() {

  }
}
