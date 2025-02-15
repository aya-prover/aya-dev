// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import org.aya.compiler.LocalVariable;
import org.aya.compiler.morphism.JavaExpr;
import org.jetbrains.annotations.NotNull;

public sealed interface AstVariable extends LocalVariable {
  record Local(int index) implements AstVariable {
    @Override public @NotNull JavaExpr ref() { return new AstExpr.RefVariable(this); }
  }
  record Arg(int nth) implements AstVariable {
    @Override public @NotNull JavaExpr ref() { return new AstExpr.RefVariable(this); }
  }
}
