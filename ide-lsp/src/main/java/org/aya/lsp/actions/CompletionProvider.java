// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import com.intellij.psi.tree.TokenSet;
import kala.collection.ArraySeq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.FreezableMutableList;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibrarySource;
import org.aya.generic.AyaDocile;
import org.aya.generic.Constants;
import org.aya.ide.action.Completion;
import org.aya.ide.action.completion.BindingCollector;
import org.aya.ide.util.XY;
import org.aya.syntax.context.ModuleSymbol;
import org.intellij.lang.annotations.MagicConstant;
import org.javacs.lsp.CompletionItem;
import org.javacs.lsp.CompletionItemKind;
import org.javacs.lsp.CompletionItemLabelDetails;
import org.javacs.lsp.CompletionList;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/// Convert result from [Completion] to [CompletionList]
public final class CompletionProvider {
  public interface Renderer {
    String render(@NotNull AyaDocile docile);
  }

  public static @NotNull CompletionList completion(
    @NotNull LibrarySource source,
    @NotNull XY xy,
    @NotNull Renderer renderer
  ) throws IOException {
    // TODO: resolve certain ModuleContext according to the qualified name at [xy].
    var completion = new Completion(source, xy, ImmutableSeq.empty(), false)
      .compute();

    var local = completion.localContext();
    var top = completion.topLevelContext();

    var location = completion.location();
    var keywords = TokenSet.EMPTY;
    if (location != null) {
      keywords = location.keywords;
    }

    var keywordCompletion = ArraySeq.wrap(keywords.getTypes())
      .map(it -> {
        var item = new CompletionItem();
        item.label = it.toString();
        item.kind = CompletionItemKind.Keyword;
        return item;
      });


    if (local == null) local = ImmutableSeq.empty();
    if (top == null) top = ImmutableSeq.empty();

    var localItems = SeqView.<Completion.Item>narrow(local.view().filter(BindingCollector::isAvailable))
      .map(it -> from(it, renderer));
    var topItems = processTopLevel(top, renderer);

    var full = localItems
      .concat(topItems)
      .concat(keywordCompletion);

    return new CompletionList(false, full.toSeq().asJava());
  }

  @MagicConstant(valuesFromClass = CompletionItemKind.class)
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
    @NotNull Completion.Item item,
    @NotNull Renderer renderer
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
        completionItem.labelDetails = new CompletionItemLabelDetails();
        completionItem.labelDetails.detail = " " + renderer.render(symbol.type());
        completionItem.labelDetails.description = "";
      }
      case Completion.Item.Decl decl -> {
        completionItem.kind = toCompletionKind(decl.kind());
        completionItem.label = decl.name();
        // https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#completionItemLabelDetails
        completionItem.labelDetails = new CompletionItemLabelDetails();
        completionItem.labelDetails.detail = " " + renderer.render(decl.type());

        completionItem.labelDetails.description = decl.disambiguous().toString();
      }
    }

    return completionItem;
  }

  /// Transform top-level [Completion.Item] to [CompletionItem], with distinction and disambiguate
  public static @NotNull ImmutableSeq<CompletionItem> processTopLevel(
    @NotNull ImmutableSeq<Completion.Item> items,
    @NotNull Renderer renderer
  ) {
    // collect all symbols that may be module.
    var map = new ModuleSymbol<Completion.Item.Decl>();
    var modules = MutableList.<Completion.Item.Module>create();

    items.view()
      .filterIsInstance(Completion.Item.Decl.class)
      .forEach(d -> map.put(d.name(), d, d.disambiguous()));

    items.view()
      .filterIsInstance(Completion.Item.Module.class)
      .forEach(m -> {
        if (m.moduleName().length() == 1) {
          if (map.contains(m.moduleName().ids().getFirst())) return;
        }

        modules.append(m);
      });

    // now, transform Completion.Item to CompletionItem

    var completionItems = FreezableMutableList.<CompletionItem>create();

    map.forEach((_, candy) -> {
      var mItems = candy.getAll().map(it -> from(it, renderer));
      if (mItems.sizeGreaterThan(1)) {
        mItems = mItems.map(i -> {
          var mItem = new CompletionItem();
          mItem.kind = i.kind;
          // dont use i.labelDetails directly
          mItem.labelDetails = new CompletionItemLabelDetails();
          mItem.labelDetails.detail = i.labelDetails.detail;
          mItem.label = i.labelDetails.description + Constants.SCOPE_SEPARATOR + i.label;
          return mItem;
        });
      }

      completionItems.appendAll(mItems);
    });

    modules.forEach(mod -> completionItems.append(from(mod, renderer)));

    return completionItems.toSeq();
  }
}
