// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.visitor;

import org.aya.concrete.Expr;
import org.jetbrains.annotations.NotNull;

public interface ExprOps extends ExprView {
  @NotNull ExprView view();
  @Override default @NotNull Expr initial() {
    return view().initial();
  }
}
