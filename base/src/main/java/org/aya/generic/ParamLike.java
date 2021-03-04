// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.generic;

import org.aya.api.ref.Var;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * @param <Expr> the type of the expression contained, either
 *               {@link Term} or {@link org.aya.concrete.Expr}.
 * @author ice1000
 */
public interface ParamLike<Expr> {
  boolean explicit();
  @NotNull Var ref();
  @NotNull Expr type();
}
