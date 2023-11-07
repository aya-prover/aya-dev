package org.aya.parser;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static org.aya.parser.FlclPsiElementTypes.*;

%%

%{
  public _FlclPsiLexer() {
    this((java.io.Reader)null);
  }
%}

// Nested doc comment processing, copied from
// https://github.com/devkt-plugins/rust-devkt/blob/master/grammar/RustLexer.flex
%{}
  /**
    * Dedicated storage for starting position of some previously successful
    * match
    */
  private int zzPostponedMarkedPos = -1;

  private boolean inHeader = true;

  /**
    * Dedicated nested-comment level counter
    */
  private int zzNestedCommentLevel = 0;
%}

%{
  IElementType imbueBlockComment() {
    assert(zzNestedCommentLevel == 0);
    yybegin(YYINITIAL);
    zzStartRead = zzPostponedMarkedPos;
    zzPostponedMarkedPos = -1;
    return AyaParserDefinitionBase.BLOCK_COMMENT;
  }
%}

%public
%class _FlclPsiLexer
%implements FlexLexer
%function advance
%type IElementType

%s IN_BLOCK_COMMENT

%unicode

///////////////////////////////////////////////////////////////////////////////////////////////////
// Identifier, adapted from AyaLexer.g4
///////////////////////////////////////////////////////////////////////////////////////////////////

AYA_SIMPLE_LETTER = [~!@#$%\^&*+=<>?/\[\]a-zA-Z_\u2200-\u22FF]
AYA_UNICODE = [\u0080-\uFEFE] | [\uFF00-\u{10FFFF}]
AYA_LETTER = {AYA_SIMPLE_LETTER} | {AYA_UNICODE}
AYA_LETTER_FOLLOW = {AYA_LETTER} | [0-9'|-]
ID = {AYA_LETTER} {AYA_LETTER_FOLLOW}* | \- {AYA_LETTER} {AYA_LETTER_FOLLOW}* | \/\\ | \\\/

///////////////////////////////////////////////////////////////////////////////////////////////////
// Literals, adapted from AyaLexer.g4
///////////////////////////////////////////////////////////////////////////////////////////////////

NUMBER = [0-9]+

///////////////////////////////////////////////////////////////////////////////////////////////////
// Comments, adapted from AyaLexer.g4
///////////////////////////////////////////////////////////////////////////////////////////////////
LINE_COMMENT        = "//" (.* | \R)
BLOCK_COMMENT_START = "/*"
BLOCK_COMMENT_END   = "*/"

%%
<YYINITIAL> {
  {LINE_COMMENT}        { return AyaParserDefinitionBase.LINE_COMMENT; }
  {BLOCK_COMMENT_START} { yybegin(IN_BLOCK_COMMENT); yypushback(2); }

  "-----"-+             { inHeader = false; return SEPARATOR; }
  ":"                   { return inHeader ? COLON : ID; }
  ";"                   { return inHeader ? SEMI : ID; }
  [(){}|,]              { return ID; }
  {ID}                  { return ID; }

  {NUMBER}              { return NUMBER; }

  // whitespace tokens are treated separated for the convenience of LaTeX translation
  \R+  { return WHITE_SPACE; }
  [ ]+ { return WHITE_SPACE; }
  \t+  { return WHITE_SPACE; }
}


///////////////////////////////////////////////////////////////////////////////////////////////////
// Comments, copied from https://github.com/devkt-plugins/rust-devkt/blob/master/grammar/RustLexer.flex
///////////////////////////////////////////////////////////////////////////////////////////////////

<IN_BLOCK_COMMENT> {
  {BLOCK_COMMENT_START}    { if (zzNestedCommentLevel++ == 0) zzPostponedMarkedPos = zzStartRead; }

  {BLOCK_COMMENT_END}      { if (--zzNestedCommentLevel == 0) return imbueBlockComment(); }

  <<EOF>>                  { zzNestedCommentLevel = 0; return imbueBlockComment(); }

  [^]                      { }
}

[^] { return BAD_CHARACTER; }
