// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.models;

import kala.collection.SeqLike;
import kala.control.Option;
import kala.value.TransientVar;
import org.aya.cli.literate.HighlightInfo;
import org.aya.lsp.utils.LspRange;
import org.aya.util.error.SourcePos;
import org.javacs.lsp.Range;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

public record HighlightResult(
  @NotNull URI uri,
  @NotNull List<Symbol> symbols
) {
  public HighlightResult(@NotNull URI uri, @NotNull SeqLike<Symbol> symbols) {
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
    @NotNull TransientVar<SourcePos> sourcePos
  ) {
    public static @NotNull Option<Symbol> from(@NotNull HighlightInfo info) {
      return kindOf(info.type())
        .map(k -> {
          var pos = info.sourcePos();
          return new Symbol(LspRange.toRange(pos), k, new TransientVar<>(pos));
        });
    }

    private static @NotNull Option<Kind> kindOf(@NotNull HighlightInfo.HighlightSymbol symbol) {
      return switch (symbol) {
        case HighlightInfo.SymDef symDef -> switch (symDef.kind()) {
          case LocalVar -> Option.none(); // maybe rainbow local variables
          case Generalized -> Option.some(Kind.Generalize);
          case Module -> Option.some(Kind.ModuleDef);
          case Fn -> Option.some(Kind.FnDef);
          case Data -> Option.some(Kind.DataDef);
          case Struct -> Option.some(Kind.StructDef);
          case Con -> Option.some(Kind.ConDef);
          case Field -> Option.some(Kind.FieldDef);
          case Prim -> Option.some(Kind.PrimDef);
          case Unknown -> Option.none();
        };
        case HighlightInfo.SymRef symRef -> switch (symRef.kind()) {
          case LocalVar -> Option.none(); // maybe rainbow local variables
          case Generalized -> Option.none(); // TODO: highlight generalized var references
          case Module -> Option.none(); // TODO: highlight module references
          case Fn -> Option.some(Kind.FnCall);
          case Data -> Option.some(Kind.DataCall);
          case Struct -> Option.some(Kind.StructCall);
          case Con -> Option.some(Kind.ConCall);
          case Field -> Option.some(Kind.FieldCall);
          case Prim -> Option.some(Kind.PrimCall);
          case Unknown -> Option.none();
        };
        case HighlightInfo.SymLit $ -> Option.none();   // handled by client
        case HighlightInfo.SymError $ -> Option.none(); // handled by client
      };
    }
  }
}
