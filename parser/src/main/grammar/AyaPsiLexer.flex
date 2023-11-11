package org.aya.parser;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static org.aya.parser.AyaPsiElementTypes.*;

%%

%{
  private boolean isRepl;
  public _AyaPsiLexer(boolean isRepl) {
    this((java.io.Reader)null);
    this.isRepl = isRepl;
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
%class _AyaPsiLexer
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
REPL_COMMAND = : {AYA_LETTER_FOLLOW}+
ID = {AYA_LETTER} {AYA_LETTER_FOLLOW}* | \- {AYA_LETTER} {AYA_LETTER_FOLLOW}* | \/\\ | \\\/

///////////////////////////////////////////////////////////////////////////////////////////////////
// Literals, adapted from AyaLexer.g4
///////////////////////////////////////////////////////////////////////////////////////////////////

NUMBER = [0-9]+

STRING = \"{STRING_CONTENT}*\"
STRING_CONTENT = [^\"\\\r\n] | \\[btnfr\"\'\\] | {OCT_ESCAPE} | {UNICODE_ESCAPE}
OCT_ESCAPE = \\{OCT_DIGIT}{OCT_DIGIT}? | \\[0-3]{OCT_DIGIT}{2}
UNICODE_ESCAPE = \\u+{HEX_DIGIT}{4}
HEX_DIGIT = [0-9a-fA-F]
OCT_DIGIT = [0-8]

///////////////////////////////////////////////////////////////////////////////////////////////////
// Unicodable keywords, generated from bnf
///////////////////////////////////////////////////////////////////////////////////////////////////

ULIFT = ulift | \u2191+
SIGMA = Sig | \u03a3
LAMBDA = \\ | fn
PI = Fn
FORALL = forall | \u2200
TO = -> | \u2192
LARROW = <- | \u2190
IMPLIES = => | \u21d2
LIDIOM = \(\| | \u2987
RIDIOM = \|\) | \u2988
LPARTIAL = \{\| | \u2983
RPARTIAL = \|\} |\u2984
LPATH = \[\| | \u27E6
RPATH = \|\] | \u27E7

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

  "infix"               { return KW_INFIX; }
  "infixl"              { return KW_INFIXL; }
  "infixr"              { return KW_INFIXR; }
  "fixl"                { return KW_FIXL; }
  "fixr"                { return KW_FIXR; }

  "tighter"             { return KW_TIGHTER; }
  "looser"              { return KW_LOOSER; }
  "example"             { return KW_EXAMPLE; }
  "counterexample"      { return KW_COUNTEREXAMPLE; }
  "Type"                { return KW_TYPE; }
  "Set"                 { return KW_SET; }
  "ISet"                { return KW_ISET; }
  "as"                  { return KW_AS; }
  "open"                { return KW_OPEN; }
  "import"              { return KW_IMPORT; }
  "public"              { return KW_PUBLIC; }
  "private"             { return KW_PRIVATE; }
  "using"               { return KW_USING; }
  "hiding"              { return KW_HIDING; }
  "coerce"              { return KW_COERCE; }
  "opaque"              { return KW_OPAQUE; }
  "inline"              { return KW_INLINE; }
  "overlap"             { return KW_OVERLAP; }
  "module"              { return KW_MODULE; }
  "match"               { return KW_MATCH; }
  "variable"            { return KW_VARIABLE; }
  "def"                 { return KW_DEF; }
  "class"               { return KW_CLASS; }
  "classifying"         { return KW_CLASSIFIYING; }
  "data"                { return KW_DATA; }
  "prim"                { return KW_PRIM; }
  "extends"             { return KW_EXTENDS; }
  "new"                 { return KW_NEW; }
  "do"                  { return KW_DO; }
  "self"                { return KW_SELF; }
  "override"            { return KW_OVERRIDE; }
  "codata"              { return KW_CODATA; }
  "let"                 { return KW_LET; }
  "in"                  { return KW_IN; }
  "completed"           { return KW_COMPLETED; }
  ":="                  { return DEFINE_AS; }
  "**"                  { return SUCHTHAT; }
  "."                   { return DOT; }
  "|"                   { return BAR; }
  ","                   { return COMMA; }
  ":"                   { return COLON; }
  "::"                  { return COLON2; }
  "{"                   { return LBRACE; }
  "}"                   { return RBRACE; }
  "("                   { return LPAREN; }
  ")"                   { return RPAREN; }
  "["                   { return LARRAY; }
  "]"                   { return RARRAY; }
  "{?"                  { return LGOAL; }
  "?}"                  { return RGOAL; }
  "@"                   { return AT; }
  "_"                   { return CALM_FACE; }

  {ULIFT}               { return KW_ULIFT; }
  {SIGMA}               { return KW_SIGMA; }
  {LAMBDA}              { return KW_LAMBDA; }
  {PI}                  { return KW_PI; }
  {FORALL}              { return KW_FORALL; }
  {TO}                  { return TO; }
  {LARROW}              { return LARROW; }
  {IMPLIES}             { return IMPLIES; }
  {LIDIOM}              { return LIDIOM; }
  {RIDIOM}              { return RIDIOM; }
  {LPARTIAL}            { return LPARTIAL; }
  {RPARTIAL}            { return RPARTIAL; }
  {LPATH}               { return LPATH; }
  {RPATH}               { return RPATH; }


  // put REPL_COMMAND before ID, or REPL_COMMAND can never be matched
  {REPL_COMMAND}        { return getTokenStart() == 0 && isRepl ? REPL_COMMAND : ID; }
  {ID}                  { return ID; }

  {NUMBER}              { return NUMBER; }
  {STRING}              { return STRING; }

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
