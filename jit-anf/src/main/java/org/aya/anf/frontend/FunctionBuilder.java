// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend;

import kala.control.Either;
import org.aya.anf.ir.IRFunction;
import org.aya.syntax.core.def.FnClauseBody;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

public class FunctionBuilder {
  private @NotNull IRFunction.FuncAttr attr = IRFunction.FuncAttr.DEFAULT;

  public @NotNull FunctionBuilder withAttr(@NotNull IRFunction.FuncAttr attr) {
    this.attr = attr;
    return this;
  }

  public @NotNull FunctionBuilder withBody(@NotNull Either<Term, FnClauseBody> body) {

    return this;
  }

  public @NotNull IRFunction build() {
    return new IRFunction(attr, null);
  }
}
