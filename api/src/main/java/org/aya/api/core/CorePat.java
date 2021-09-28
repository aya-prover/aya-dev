// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.core;

import org.aya.api.distill.AyaDocile;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public interface CorePat extends AyaDocile {
  @Nullable LocalVar as();
  boolean explicit();
  @NotNull CoreTerm type();
  @NotNull CoreTerm toTerm();
  @NotNull Arg<? extends @NotNull CoreTerm> toArg();
}
