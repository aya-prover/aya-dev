// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.anf.frontend.compile;

import kala.control.Either;
import org.aya.anf.ir.struct.IrComp;
import org.aya.syntax.core.def.FnDef;
import org.jetbrains.annotations.NotNull;

/// `FuncBuilder` builds the body of a function from a given `Term`.
/// This class does not use a functional design pattern as some semantic context used for `IrFunc`
/// construction are persistent and do not follow a stack structure (e.g., unnamed bindings whose
/// name are to be generated after suffcient context gathering).
public class FuncBuilder {

  private final @NotNull FnDef fn;
  private final @NotNull LoweringContext ctx;

  public FuncBuilder(final @NotNull FnDef fn) {
    this.fn = fn;
    ctx = LoweringContext.fromFuncDef(fn);
  }

  public @NotNull IrComp build() {
    return switch (fn.body()) {
      case Either.Left(var term) -> ctx.buildAtCtx().make(term);
      case Either.Right(var body) -> {

        yield null;
      }
    };
  }
}
