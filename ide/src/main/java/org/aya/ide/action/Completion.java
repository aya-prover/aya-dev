// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.AyaDocile;
import org.aya.prettier.BasePrettier;
import org.aya.prettier.Tokens;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.StmtVisitor;
import org.aya.syntax.ref.AnyVar;
import org.aya.util.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public class Completion {
  // public static final @NotNull ImmutableSeq<CompletionItem> KEYWORD = ImmutableArray.Unsafe.wrap(AyaParserDefinitionBase.KEYWORDS.getTypes())
  //   .map(type -> {
  //     var item = new CompletionItem();
  //     item.label = type.toString();
  //     item.kind = CompletionItemKind.Keyword;
  //     item.insertText = item.label;
  //     item.insertTextFormat = InsertTextFormat.PlainText;
  //     return item;
  //   });

  public record Telescope(@NotNull ImmutableSeq<StmtVisitor.Type> telescope, @NotNull StmtVisitor.Type result) {
  }

  // FIXME: ugly though, fix me later
  public sealed interface CompletionItemu {
    sealed interface Symbol extends CompletionItemu {
      @NotNull String name();
      @NotNull Telescope type();
    }

    record Decl(
      @NotNull ModuleName inModule,
      @Override @NotNull String name,
      @Override @NotNull Telescope type
    ) implements Symbol { }

    record Module(@NotNull ModuleName moduleName) implements CompletionItemu { }

    record Local(@NotNull AnyVar var, @NotNull StmtVisitor.Type userType) implements AyaDocile, Symbol {
      @Override
      public @NotNull String name() {
        return var.name();
      }

      @Override
      public @NotNull Telescope type() {
        return new Telescope(ImmutableSeq.empty(), userType);
      }

      @Override
      public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
        var typeDoc = userType.toDocile();
        var realTypeDoc = typeDoc == null
          ? Doc.empty()
          : Doc.sep(Tokens.HAS_TYPE, typeDoc.toDoc(options));

        return Doc.sepNonEmpty(BasePrettier.varDoc(var), realTypeDoc);
      }
    }
  }

  /// Do the similar job as {@link org.aya.resolve.visitor.StmtPreResolver}
  public static @NotNull ImmutableSeq<CompletionItemu> resolveTopLevel() {
    throw new UnsupportedOperationException("TODO");
  }
}
