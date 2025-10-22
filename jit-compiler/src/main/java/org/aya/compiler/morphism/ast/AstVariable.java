// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism.ast;

import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.NotNull;

public sealed interface AstVariable extends Docile, AstValue {
  record Local(int index) implements AstVariable {
    @Override public @NotNull Doc toDoc() {
      return Doc.plain("%" + index) ;
    }
  }
  record Arg(int nth) implements AstVariable {
    @Override public @NotNull Doc toDoc() {
      return Doc.plain("arg%" + nth);
    }
  }
  record Capture(int nth) implements AstVariable {
    @Override public @NotNull Doc toDoc() {
      return Doc.plain("capture%" + nth);
    }
  }
}
