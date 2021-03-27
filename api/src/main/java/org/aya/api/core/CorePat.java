// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.core;

import org.aya.api.ref.LocalVar;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public interface CorePat extends Docile {
  @Nullable LocalVar as();
  boolean explicit();
  @NotNull CoreTerm type();
  @NotNull CoreTerm toTerm();
}
