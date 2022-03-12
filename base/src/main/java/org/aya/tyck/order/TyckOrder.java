// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.order;

import org.jetbrains.annotations.NotNull;

public sealed interface TyckOrder {
  @NotNull TyckUnit unit();

  /** header order */
  record Head(@NotNull TyckUnit unit) implements TyckOrder {}

  /** body order */
  record Body(@NotNull TyckUnit unit) implements TyckOrder {}
}
