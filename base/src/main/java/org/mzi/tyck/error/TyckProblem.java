// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.error;

import org.jetbrains.annotations.NotNull;
import org.mzi.api.error.Problem;
import org.mzi.api.error.SourcePos;
import org.mzi.concrete.Expr;

/**
 * @author ice1000
 */
public interface TyckProblem extends Problem {
  @NotNull Expr expr();

  @Override default @NotNull SourcePos sourcePos() {
    return expr().sourcePos();
  }
}
