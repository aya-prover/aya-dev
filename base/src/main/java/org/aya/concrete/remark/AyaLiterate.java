// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.remark;

import kala.value.MutableValue;
import org.aya.concrete.Expr;
import org.aya.concrete.remark.code.CodeOptions;
import org.aya.core.def.UserDef;
import org.aya.literate.Literate;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Language;
import org.aya.pretty.doc.Style;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.tyck.Result;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author hoshino
 */
public interface AyaLiterate {
  record AyaError(@NotNull MutableValue<AnyVar> def, @Override @NotNull SourcePos sourcePos) implements Literate {
    @Override public @NotNull Doc toDoc() {
      if (def.get() instanceof DefVar<?, ?> defVar && defVar.core instanceof UserDef<?> userDef) {
        var problems = userDef.problems;
        if (problems == null) return Doc.styled(Style.bold(), Doc.english("No error message."));
        return Doc.vcat(problems.map(problem -> problem.brief(AyaPrettierOptions.informative())));
      }
      return Doc.styled(Style.bold(), Doc.english("Not a definition that can obtain error message."));
    }
  }

  /**
   * A code snippet. For code blocks, see {@link AyaCodeBlock}
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

  /**
   * A code block.
   *
   * @implNote the content which this code block hold can be illegal, like
   * <pre>
   * ```aya<br/>
   * def foo : Nat =><br/>
   * ```<br/>
   * </pre>
   * Note that the language has to be <code>aya</code> or <code>aya-hidden</code>
   */
  final class AyaCodeBlock implements Literate.AnyCodeBlock {
    public final static String LANGUAGE_AYA = "aya";
    public final static String LANGUAGE_AYA_HIDDEN = "aya-hidden";

    public static boolean isAya(@NotNull String language) {
      return language.equalsIgnoreCase(LANGUAGE_AYA) || language.equalsIgnoreCase(LANGUAGE_AYA_HIDDEN);
    }

    public final @Nullable SourcePos sourcePos;
    public final @NotNull String raw;
    public final boolean isHidden;

    public @Nullable Doc highlighted;

    public AyaCodeBlock(@Nullable SourcePos sourcePos, @NotNull String raw, boolean isHidden) {
      this.sourcePos = sourcePos;
      this.raw = raw;
      this.isHidden = isHidden;
    }

    @Override
    public @NotNull String language() {
      return isHidden ? LANGUAGE_AYA_HIDDEN : LANGUAGE_AYA;
    }

    @Override
    public @NotNull String code() {
      return raw;
    }

    @Override
    public @Nullable SourcePos sourcePos() {
      return sourcePos;
    }

    @Override public @NotNull Doc toDoc() {
      if (isHidden) return Doc.empty();
      var doc = highlighted != null ? highlighted : Doc.plain(raw);
      return Doc.codeBlock(Language.of(language()), doc);
    }
  }
}
