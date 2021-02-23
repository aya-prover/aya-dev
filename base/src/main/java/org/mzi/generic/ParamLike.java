// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.generic;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.Var;

/**
 * @param <Expr> the type of the expression contained, either
 *               {@link org.mzi.core.term.Term} or {@link org.mzi.concrete.Expr}.
 * @author ice1000
 */
public interface ParamLike<Expr> {
  boolean explicit();
  @NotNull Var ref();
  @NotNull Expr type();
}
