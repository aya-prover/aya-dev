// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.remark;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.Expr;
import org.aya.literate.Literate;
import org.aya.literate.parser.InterestingLanguage;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Language;
import org.aya.tyck.Result;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author hoshino
 */
public interface AyaLiterate {
  @NotNull InterestingLanguage<AyaVisibleCodeBlock> VISIBLE_AYA = InterestingLanguage.of("aya"::equalsIgnoreCase,
    AyaVisibleCodeBlock::new);
  @NotNull InterestingLanguage<AyaHiddenCodeBlock> HIDDEN_AYA = InterestingLanguage.of("aya-hidden"::equalsIgnoreCase,
    AyaHiddenCodeBlock::new);
  @NotNull ImmutableSeq<InterestingLanguage<?>> AYA = ImmutableSeq.of(VISIBLE_AYA, HIDDEN_AYA);

  class AyaCodeBlock extends Literate.CodeBlock {
    public AyaCodeBlock(@NotNull String language, @NotNull String code, @Nullable SourcePos sourcePos) {
      super(language, code, sourcePos);
    }
  }

  final class AyaVisibleCodeBlock extends AyaCodeBlock {
    public AyaVisibleCodeBlock(@NotNull String language, @NotNull String code, @Nullable SourcePos sourcePos) {
      super(language, code, sourcePos);
    }
  }

  final class AyaHiddenCodeBlock extends AyaCodeBlock {
    public AyaHiddenCodeBlock(@NotNull String language, @NotNull String code, @Nullable SourcePos sourcePos) {
      super(language, code, sourcePos);
    }

    @Override public @NotNull Doc toDoc() {
      return Doc.empty();
    }
  }

  /**
   * Aya inline code. For code blocks, see {@link AyaVisibleCodeBlock} and {@link AyaHiddenCodeBlock}
   */
  final class AyaInlineCode extends Literate.InlineCode {
    public @Nullable Expr expr;
    public @Nullable Result tyckResult;
    public final @NotNull CodeOptions options;

    public AyaInlineCode(@NotNull String code, @NotNull SourcePos sourcePos, @NotNull CodeOptions options) {
      super(code, sourcePos);

      this.options = options;
    }

    @Override public @NotNull Doc toDoc() {
      if (tyckResult == null) {
        if (expr != null) return Doc.code(Language.Builtin.Aya, expr.toDoc(options.options()));
        else return Doc.code("Error");
      }
      assert expr != null;
      return Doc.code(Language.Builtin.Aya, (switch (options.showCode()) {
        case Concrete -> expr;
        case Core -> tyckResult.wellTyped();
        case Type -> tyckResult.type();
      }).toDoc(options.options()));
    }
  }
}
