// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.util;

import org.aya.api.core.CoreTerm;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @param <T> the type of expressions, can be {@link CoreTerm} or
 *            {@link org.aya.api.concrete.ConcreteExpr}.
 * @author ice1000
 */
public record Arg<T>(@NotNull T term, boolean explicit) {
  @Contract("_ -> new") public static <T> @NotNull Arg<T> explicit(@NotNull T term) {
    return new Arg<>(term, true);
  }

  @Contract("_ -> new") public static <T> @NotNull Arg<T> implicit(@NotNull T term) {
    return new Arg<>(term, false);
  }

  public @NotNull Arg<T> implicitify() {
    return new Arg<>(term, false);
  }
}
