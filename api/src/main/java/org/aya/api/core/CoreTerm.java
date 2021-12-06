// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.core;

import org.aya.api.distill.AyaDocile;
import org.aya.api.ref.Var;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author kiva, ice1000
 */
@ApiStatus.NonExtendable
public interface CoreTerm extends AyaDocile {
  @NotNull CoreTerm rename();
  /** @return Number of usages of the given var. */
  int findUsages(@NotNull Var var);
}
