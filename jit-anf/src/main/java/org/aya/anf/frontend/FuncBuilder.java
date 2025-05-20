// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend;

import kala.control.Either;
import org.aya.anf.ir.IRFunc;
import org.aya.syntax.core.def.FnClauseBody;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public class FuncBuilder {
  private @NotNull IRFunc.FuncAttr attr = IRFunc.FuncAttr.DEFAULT;

  public @NotNull FuncBuilder withAttr(@NotNull IRFunc.FuncAttr attr) {
    this.attr = attr;
    return this;
  }

  public @NotNull FuncBuilder withBody(@NotNull Either<Term, FnClauseBody> body) {

    return this;
  }

  public @NotNull IRFunc build() {
    return new IRFunc(attr, null);
  }
}
