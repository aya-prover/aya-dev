// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.library.source.LibrarySource;
import org.aya.ide.action.Completion;
import org.aya.ide.util.XY;
import org.aya.lsp.server.AyaLanguageServer;
import org.aya.parser.AyaParserDefinitionBase;
import org.aya.pretty.doc.Doc;
import org.aya.util.PrettierOptions;
import org.javacs.lsp.CompletionItem;
import org.javacs.lsp.CompletionItemKind;
import org.javacs.lsp.CompletionList;
import org.jetbrains.annotations.NotNull;

public final class CompletionProvider {
  public static final @NotNull ImmutableSeq<CompletionItem> KEYWORDS = ImmutableArray.Unsafe
    .wrap(AyaParserDefinitionBase.KEYWORDS.getTypes())
    .map(it -> {
      var item = new CompletionItem();
      item.label = it.toString();
      item.kind = CompletionItemKind.Keyword;
      return item;
    });

  public static @NotNull CompletionList completion(
    @NotNull AyaLanguageServer lsp,
    @NotNull PrettierOptions options,
    @NotNull LibrarySource source,
    @NotNull XY xy
  ) {
    // TODO: resolve certain ModuleContext according to the qualified name at [xy].
    var completion = new Completion(source, xy, ImmutableSeq.empty(), false)
      .compute();

    var local = completion.localContext();
    var top = completion.topLevelContext();

    if (local == null) local = ImmutableSeq.empty();
    if (top == null) top = ImmutableSeq.empty();

    var full = SeqView.<Completion.Item>narrow(local.view())
      .concat(top)
      .map(it -> from(lsp, options, it))
      .concat(KEYWORDS);

    return new CompletionList(false, full.toSeq().asJava());
  }

  public static int toCompletionKind(@NotNull Completion.Item.Decl.Kind kind) {
    return switch (kind) {
      case Generalized, Data -> CompletionItemKind.Struct;
      case Fn -> CompletionItemKind.Function;
      case Con -> CompletionItemKind.Constructor;
      case Class -> CompletionItemKind.Class;
      case Member -> CompletionItemKind.Field;
      case Prim -> CompletionItemKind.Interface;
    };
  }

  public static @NotNull CompletionItem from(
    @NotNull AyaLanguageServer lsp,
    @NotNull PrettierOptions options,
    @NotNull Completion.Item item
  ) {
    var completionItem = new CompletionItem();

    switch (item) {
      case Completion.Item.Module module -> {
        completionItem.kind = CompletionItemKind.Module;
        completionItem.label = module.moduleName().toString();
      }
      case Completion.Item.Local symbol -> {
        completionItem.kind = CompletionItemKind.Variable;
        completionItem.label = symbol.name();
        var typeDoc = symbol.toDoc(options);
        var sepTypeDoc = Doc.stickySep(Doc.ONE_WS, typeDoc);
        completionItem.detail = lsp.render(sepTypeDoc);
      }
      case Completion.Item.Decl decl -> {
        completionItem.kind = toCompletionKind(decl.kind());
        completionItem.label = decl.name();
        // https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#completionItemLabelDetails
        // TODO: deal with ambiguous, we need [labelDetails] property
        var typeDoc = decl.type().toDoc(options);
        var sepTypeDoc = Doc.stickySep(Doc.ONE_WS, typeDoc);
        completionItem.detail = lsp.render(sepTypeDoc);
      }
    }

    return completionItem;
  }
}
