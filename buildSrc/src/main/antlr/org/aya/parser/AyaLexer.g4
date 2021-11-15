lexer grammar AyaLexer;

REPL_COMMAND : ':' [a-zA-Z0-9-]+;

// Do not change the line like `---- AyaLexer xxx: XXX`
// They are used to generate `GeneratedLexerTokens` class.

// ---- AyaLexer begin: Keywords

// associativities
INFIX  : 'infix';
INFIXL : 'infixl';
INFIXR : 'infixr';

// operator precedence
TIGHTER : 'tighter';
LOOSER : 'looser';

// samples
EXAMPLE : 'example';
COUNTEREXAMPLE : 'counterexample';

// universe
ULEVEL : 'universe';
TYPE : 'Type';

// other keywords
// principal: add `_KW` suffix to avoid conflict with a potential rule name.
// if it seems impossible, then forget about it.
AS : 'as';
OPEN : 'open';
IMPORT : 'import';
PUBLIC : 'public';
PRIVATE : 'private';
USING : 'using';
HIDING : 'hiding';
COERCE : 'coerce';
OPAQUE : 'opaque';
INLINE : 'inline';
OVERLAP : 'overlap';
MODULE_KW : 'module';
BIND_KW : 'bind';
MATCH : 'match';
ABSURD : 'impossible';
VARIABLE : 'variable';
DEF : 'def';
STRUCT : 'struct';
DATA : 'data';
PRIM : 'prim';
EXTENDS : 'extends';
NEW_KW : 'new';
LSUC_KW : 'lsuc';
LMAX_KW : 'lmax';
PATTERN_KW : 'pattern';

// symbols
SIGMA : 'Sig' | '\u03A3';
LAMBDA : '\\' | '\u03BB';
PI : 'Pi' | '\u03A0';
FORALL : 'forall' | '\u2200';

// ---- AyaLexer end: Keywords

TO : '->' | '\u2192';
IMPLIES : '=>' | '\u21D2';
SUCHTHAT : '**';
DOT : '.';
BAR : '|';
COMMA : ',';
COLON : ':';
COLON2 : '::';

// markers
LBRACE : '{';
RBRACE : '}';
LPAREN : '(';
RPAREN : ')';
LGOAL : '{?';
RGOAL : '?}';

// literals
NUMBER : [0-9]+;
CALM_FACE : '_';
STRING : INCOMPLETE_STRING '"';
INCOMPLETE_STRING : '"' (~["\\\r\n] | ESCAPE_SEQ)*;
fragment ESCAPE_SEQ : '\\' [btnfr"'\\] | OCT_ESCAPE | UNICODE_ESCAPE;
fragment OCT_ESCAPE : '\\' OCT_DIGIT OCT_DIGIT? | '\\' [0-3] OCT_DIGIT OCT_DIGIT;
fragment UNICODE_ESCAPE : '\\' 'u'+ HEX_DIGIT HEX_DIGIT HEX_DIGIT HEX_DIGIT;
fragment HEX_DIGIT : [0-9a-fA-F];
fragment OCT_DIGIT : [0-8];

// identifier
fragment AYA_SIMPLE_LETTER : [~!@#$%^&*+=<>?/|[\u005Da-zA-Z_\u2200-\u22FF];
fragment AYA_UNICODE : [\u0080-\uFEFE] | [\uFF00-\u{10FFFF}]; // exclude U+FEFF which is a truly invisible char
fragment AYA_LETTER : AYA_SIMPLE_LETTER | AYA_UNICODE;
fragment AYA_LETTER_FOLLOW : AYA_LETTER | [0-9'-];
ID : AYA_LETTER AYA_LETTER_FOLLOW* | '-' AYA_LETTER AYA_LETTER_FOLLOW*;

// whitespaces
WS : [ \t\r\n]+ -> channel(HIDDEN);
fragment COMMENT_CONTENT : ~[\r\n]*;
DOC_COMMENT : '--|' COMMENT_CONTENT;
LINE_COMMENT : '--' COMMENT_CONTENT -> channel(HIDDEN);
COMMENT : '{-' (COMMENT|.)*? '-}' -> channel(HIDDEN);

// avoid token recognition error in REPL
ERROR_CHAR : .;
