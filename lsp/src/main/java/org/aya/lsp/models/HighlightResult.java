// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.models;

import kala.collection.SeqLike;
import org.aya.lsp.utils.ForIDEA;
import org.aya.lsp.utils.LspRange;
import org.aya.util.error.SourcePos;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public record HighlightResult(
  @NotNull String uri,
  @NotNull List<Symbol> symbols
) {
  public HighlightResult(@NotNull String uri, @NotNull SeqLike<Symbol> symbols) {
    this(uri, symbols.collect(Collectors.toList()));
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
    @NotNull ForIDEA<SourcePos> sourcePos
  ) {
    public Symbol(@NotNull SourcePos sourcePos, @NotNull HighlightResult.Kind kind) {
      this(LspRange.toRange(sourcePos), kind, new ForIDEA<>(sourcePos));
    }
  }
}
