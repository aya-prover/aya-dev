// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import org.aya.parser.AyaParserDefinitionBase;
import org.javacs.lsp.CompletionItem;
import org.javacs.lsp.CompletionItemKind;
import org.javacs.lsp.InsertTextFormat;
import org.jetbrains.annotations.NotNull;

public class Completion {
  public static final @NotNull ImmutableSeq<CompletionItem> KEYWORD = ImmutableArray.Unsafe.wrap(AyaParserDefinitionBase.KEYWORDS.getTypes())
    .map(type -> {
      var item = new CompletionItem();
      item.label = type.toString();
      item.kind = CompletionItemKind.Keyword;
      item.insertText = item.label;
      item.insertTextFormat = InsertTextFormat.PlainText;
      return item;
    });
}
