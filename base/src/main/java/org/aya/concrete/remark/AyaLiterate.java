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
  @NotNull InterestingLanguage VISIBLE_AYA = "aya"::equalsIgnoreCase;
  @NotNull InterestingLanguage HIDDEN_AYA = "aya-hidden"::equalsIgnoreCase;
  @NotNull ImmutableSeq<InterestingLanguage> AYA = ImmutableSeq.of(VISIBLE_AYA, HIDDEN_AYA);

  static boolean isAya(@NotNull String language) {
    return AYA.anyMatch(s -> s.test(language));
  }

  static boolean isVisibleAya(@NotNull String language) {
    return VISIBLE_AYA.test(language);
  }

  static @NotNull Literate.CodeBlock visibleAyaCodeBlock(@NotNull String code, @NotNull SourcePos sourcePos) {
    return new Literate.CodeBlock("aya", code, sourcePos);
  }

  /**
   * Aya inline code. For code blocks, see {@link CodeBlock}
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
