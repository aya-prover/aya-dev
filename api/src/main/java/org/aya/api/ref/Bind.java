// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.ref;

import org.aya.api.core.term.CoreTerm;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author kiva
 */
@ApiStatus.NonExtendable
public interface Bind {
  @Contract(pure = true) @NotNull Var ref();
  @Contract(pure = true) @Nullable CoreTerm type();
  @Contract(pure = true) boolean explicit();
}
