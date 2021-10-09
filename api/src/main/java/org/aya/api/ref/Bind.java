// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.ref;

import org.aya.api.core.CoreTerm;
import org.aya.api.distill.AyaDocile;
import org.aya.api.util.Arg;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kiva
 */
@ApiStatus.NonExtendable
public interface Bind extends AyaDocile {
  @Contract(pure = true) @NotNull LocalVar ref();
  @Contract(pure = true) @Nullable CoreTerm type();
  @Contract(pure = true) boolean explicit();
  @Contract(" -> new") @NotNull Arg<? extends CoreTerm> toArg();
  @Contract(" -> new") @NotNull CoreTerm toTerm();
}
