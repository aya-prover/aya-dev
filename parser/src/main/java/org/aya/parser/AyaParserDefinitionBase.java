// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.parser;

import com.intellij.lang.PsiParser;
import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.FlexLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class AyaParserDefinitionBase extends ParserDefBase.WithFile {
  public AyaParserDefinitionBase(@NotNull IFileElementType file) {
    super(file);
  }

  public static @NotNull FlexLexer createLexer(boolean isRepl) {
    return new _AyaPsiLexer(isRepl);
  }

  @Override public @NotNull Lexer createLexer(Project project) {
    return new FlexAdapter(createLexer(false));
  }

  @Override public @NotNull PsiParser createParser(Project project) {
    return new AyaPsiParser();
  }

  @Override public @NotNull TokenSet getStringLiteralElements() {
    return STRINGS;
  }

  public static final @NotNull TokenSet IDENTIFIERS = TokenSet.create(AyaPsiElementTypes.ID, AyaPsiElementTypes.REPL_COMMAND);
  public static final @NotNull TokenSet STRINGS = TokenSet.create(AyaPsiElementTypes.STRING);
  /** non-text symbols that should be highlighted like keywords */
  public static final @NotNull TokenSet MARKERS = TokenSet.create(
    AyaPsiElementTypes.TO,
    AyaPsiElementTypes.LARROW,
    AyaPsiElementTypes.IMPLIES,
    AyaPsiElementTypes.DEFINE_AS,
    AyaPsiElementTypes.SUCHTHAT,
    AyaPsiElementTypes.DOT,
    AyaPsiElementTypes.BAR,
    AyaPsiElementTypes.COLON
  );
  /** text keywords */
  public static final @NotNull TokenSet KEYWORDS = TokenSet.create(
    AyaPsiElementTypes.KW_AS,
    AyaPsiElementTypes.KW_CODATA,
    AyaPsiElementTypes.KW_COERCE,
    AyaPsiElementTypes.KW_COMPLETED,
    AyaPsiElementTypes.KW_DATA,
    AyaPsiElementTypes.KW_DEF,
    AyaPsiElementTypes.KW_DO,
    AyaPsiElementTypes.KW_SELF,
    AyaPsiElementTypes.KW_OVERRIDE,
    AyaPsiElementTypes.KW_EXAMPLE,
    AyaPsiElementTypes.KW_EXTENDS,
    AyaPsiElementTypes.KW_FORALL,
    AyaPsiElementTypes.KW_HIDING,
    AyaPsiElementTypes.KW_IMPORT,
    AyaPsiElementTypes.KW_IN,
    AyaPsiElementTypes.KW_INFIX,
    AyaPsiElementTypes.KW_INFIXL,
    AyaPsiElementTypes.KW_INFIXR,
    AyaPsiElementTypes.KW_FIXL,
    AyaPsiElementTypes.KW_FIXR,
    AyaPsiElementTypes.KW_INLINE,
    AyaPsiElementTypes.KW_LAMBDA,
    AyaPsiElementTypes.KW_LET,
    AyaPsiElementTypes.KW_LOOSER,
    AyaPsiElementTypes.KW_MATCH,
    AyaPsiElementTypes.KW_MODULE,
    AyaPsiElementTypes.KW_NEW,
    AyaPsiElementTypes.KW_OPAQUE,
    AyaPsiElementTypes.KW_OPEN,
    AyaPsiElementTypes.KW_OVERLAP,
    AyaPsiElementTypes.KW_PI,
    AyaPsiElementTypes.KW_PRIM,
    AyaPsiElementTypes.KW_PRIVATE,
    AyaPsiElementTypes.KW_PUBLIC,
    AyaPsiElementTypes.KW_SIGMA,
    AyaPsiElementTypes.KW_CLASS,
    AyaPsiElementTypes.KW_CLASSIFIYING,
    AyaPsiElementTypes.KW_TIGHTER,
    AyaPsiElementTypes.KW_TYPE,
    AyaPsiElementTypes.KW_PROP,
    AyaPsiElementTypes.KW_SET,
    AyaPsiElementTypes.KW_ISET,
    AyaPsiElementTypes.KW_ULIFT,
    AyaPsiElementTypes.KW_USING,
    AyaPsiElementTypes.KW_VARIABLE
  );
  /** Anything that has a unicode variant. Keep in touch with AyaPsiLexer.flex */
  public static final @NotNull TokenSet UNICODES = TokenSet.create(
    AyaPsiElementTypes.KW_ULIFT,
    AyaPsiElementTypes.KW_SIGMA,
    AyaPsiElementTypes.KW_LAMBDA,
    AyaPsiElementTypes.KW_PI,
    AyaPsiElementTypes.KW_FORALL,
    AyaPsiElementTypes.TO,
    AyaPsiElementTypes.LARROW,
    AyaPsiElementTypes.IMPLIES,
    AyaPsiElementTypes.LIDIOM,
    AyaPsiElementTypes.RIDIOM
  );

  public static final @NotNull TokenSet DELIMITERS = TokenSet.create(
    AyaPsiElementTypes.LPAREN,
    AyaPsiElementTypes.RPAREN,
    AyaPsiElementTypes.LBRACE,
    AyaPsiElementTypes.RBRACE
  );
}
