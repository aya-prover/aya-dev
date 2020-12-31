// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.api.ref;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.core.term.CoreTerm;

/**
 * @author kiva
 */
@ApiStatus.NonExtendable
public interface Bind {
  @Contract(pure = true) @NotNull Var ref();
  @Contract(pure = true) @Nullable CoreTerm type();
  @Contract(pure = true) boolean explicit();
}
