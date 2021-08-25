// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public record CompiledAya(
  @NotNull ImmutableSeq<SerDef> defs
) implements Serializable {
  public static @NotNull CompiledAya from(@NotNull ImmutableSeq<SerDef> defs) {
    return new CompiledAya(defs);
  }
}
