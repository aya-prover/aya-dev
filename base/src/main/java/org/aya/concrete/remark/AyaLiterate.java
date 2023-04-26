// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.remark;

import org.aya.concrete.Expr;
import org.aya.literate.Literate;
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
  String LANGUAGE_AYA = "aya";
  String LANGUAGE_AYA_HIDDEN = "aya-hidden";
  static boolean isAya(@NotNull String language) {
    return language.equalsIgnoreCase(LANGUAGE_AYA) || language.equalsIgnoreCase(LANGUAGE_AYA_HIDDEN);
  }

  /**
   * A code snippet. For code blocks, see {@link CommonCodeBlock}
   */
  final class AyaCode extends Literate.CommonCode {
    public @Nullable Expr expr;
    public @Nullable Result tyckResult;
    public final @NotNull CodeOptions options;

    public AyaCode(@NotNull String code, @NotNull SourcePos sourcePos, @NotNull CodeOptions options) {
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
