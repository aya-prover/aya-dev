// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.models;

import kala.collection.Seq;
import kala.value.TransientVar;
import org.aya.lsp.utils.LspRange;
import org.aya.util.error.SourcePos;
import org.javacs.lsp.Range;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.List;

public record HighlightResult(@NotNull URI uri, @NotNull List<Symbol> symbols) {
  public HighlightResult(@NotNull URI uri, @NotNull Seq<Symbol> symbols) {
    this(uri, symbols.asJava());
  }

  public enum Kind {
    // definitions
    ModuleDef, FnDef, DataDef, StructDef, ConDef, FieldDef, PrimDef,
    // expressions
    Generalize, FnCall, DataCall, StructCall, ConCall, FieldCall, PrimCall,
  }

  public record Symbol(
    @NotNull Range range,
    @NotNull HighlightResult.Kind kind,
    @NotNull TransientVar<SourcePos> sourcePos
  ) {
    public Symbol(@NotNull SourcePos sourcePos, @NotNull HighlightResult.Kind kind) {
      this(LspRange.toRange(sourcePos), kind, new TransientVar<>(sourcePos));
    }
  }
}
