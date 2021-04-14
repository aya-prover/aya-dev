// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.error;

import org.aya.api.error.Problem;
import org.aya.api.error.SourcePos;
import org.aya.concrete.Expr;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface ExprProblem extends Problem {
  @NotNull Expr expr();

  @Override default @NotNull SourcePos sourcePos() {
    return expr().sourcePos();
  }
}
