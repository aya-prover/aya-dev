// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * @param <T> the type of expressions.
 *            In Aya, it is either core term, core pattern, concrete term, or concrete pattern.
 * @author ice1000
 */
@Debug.Renderer(text = "toDoc(org.aya.util.distill.DistillerOptions.debug(), this).debugRender()")
public record Arg<T>(@NotNull T term, boolean explicit) {
  public @NotNull Arg<T> update(@NotNull T term) {
    return term == term() ? this : new Arg<>(term, explicit);
  }

  public @NotNull Arg<T> descent(@NotNull UnaryOperator<@NotNull T> f) {
    return update(f.apply(term));
  }
}
