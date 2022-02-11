// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.aya.concrete.Expr;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Problem;
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
