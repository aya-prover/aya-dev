// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ref;

import org.aya.core.term.Term;
import org.aya.generic.Arg;
import org.aya.generic.AyaDocile;
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
  @Contract(pure = true) @Nullable Term type();
  @Contract(pure = true) boolean explicit();
  @Contract(" -> new") @NotNull Arg<? extends Term> toArg();
  @Contract(" -> new") @NotNull Term toTerm();
}
