// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.error;

import org.aya.api.concrete.ConcreteExpr;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface ExprProblem extends Problem {
  @NotNull ConcreteExpr expr();

  @Override default @NotNull SourcePos sourcePos() {
    return expr().sourcePos();
  }
}
