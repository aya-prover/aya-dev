// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.literate;

import kala.collection.immutable.ImmutableSeq;
import org.aya.literate.Literate;
import org.aya.literate.parser.InterestingLanguage;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Language;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.core.term.Term;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author hoshino
 */
public interface AyaLiterate {
  /**
   * Languages recognized by `.aya.md` compiler.
   */
  @NotNull ImmutableSeq<InterestingLanguage<?>> LANGUAGES = ImmutableSeq.of(
    InterestingLanguage.of("aya"::equalsIgnoreCase,
      AyaVisibleCodeBlock::new),
    InterestingLanguage.of("aya-hidden"::equalsIgnoreCase,
      AyaHiddenCodeBlock::new),
    InterestingLanguage.of("aya-lexer"::equalsIgnoreCase,
      AyaLexerCodeBlock::new));

  /**
   * An instance of an Aya code block contains a code block that is intended to be type checked.
   * So, if we do not intend to type check it, even if the language is Aya, we should not
   * extend this class.
   */
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

    @Override public @NotNull Doc toDoc() { return Doc.empty(); }
  }

  final class AyaLexerCodeBlock extends Literate.CodeBlock {
    public AyaLexerCodeBlock(@NotNull String language, @NotNull String code, @Nullable SourcePos sourcePos) {
      super(language, code, sourcePos);
    }
  }

  record TyckResult(Term wellTyped, Term type) { }

  /**
   * Aya inline code. For code blocks, see {@link AyaVisibleCodeBlock} and {@link AyaHiddenCodeBlock}
   */
  final class AyaInlineCode extends Literate.InlineCode {
    public @Nullable ImmutableSeq<Expr.Param> params;
    public @Nullable WithPos<Expr> expr;
    public @Nullable TyckResult tyckResult;
    public final @NotNull CodeOptions options;

    public AyaInlineCode(@NotNull String code, @NotNull SourcePos sourcePos, @NotNull CodeOptions options) {
      super(code, sourcePos);

      this.options = options;
    }

    @Override public @NotNull Doc toDoc() {
      if (tyckResult == null) {
        if (expr != null) return Doc.code(Language.Builtin.Aya,
          expr.data().toDoc(options.options()));
        else return Doc.code("Error");
      }
      assert expr != null;
      return Doc.code(Language.Builtin.Aya, (switch (options.showCode()) {
        case Concrete -> expr.data();
        case Core -> tyckResult.wellTyped();
        case Type -> tyckResult.type();
      }).toDoc(options.options()));
    }
  }
}
