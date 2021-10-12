lexer grammar AyaLexer;

INFIX : '`' ID '`';
POSTFIX : '`' ID;

// operator precedence
TIGHTER : 'tighter';
LOOSER : 'looser';

// samples
EXAMPLE : 'example';
COUNTEREXAMPLE : 'counterexample';

// associativities
INFIXN : 'infix';
INFIXL : 'infixl';
INFIXR : 'infixr';
FIX : 'fix';
FIXL : 'fixl';
FIXR : 'fixr';
TWIN : 'twin';

// universe
ULEVEL : 'universe';
TYPE : 'Type';

// other keywords
AS : 'as';
OPEN : 'open';
IMPORT : 'import';
PUBLIC : 'public';
PRIVATE : 'private';
USING : 'using';
HIDING : 'hiding';
COERCE : 'coerce';
ERASE : 'erase';
INLINE : 'inline';
MODULE_KW : 'module';
BIND_KW : 'bind';
MATCH : 'match';
ABSURD : 'impossible';
VARIABLE : 'variable';
ABUSING : 'abusing';
DEF : 'def';
STRUCT : 'struct';
DATA : 'data';
PRIM : 'prim';
EXTENDS : 'extends';
NEW_KW : 'new';
LSUC_KW : 'lsuc';
LMAX_KW : 'lmax';

// symbols
SIGMA : 'Sig' | '\u03A3';
LAMBDA : '\\' | '\u03BB';
PI : 'Pi' | '\u03A0';
FORALL : 'forall' | '\u2200';
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
INCOMPLETE_STRING : '"' (~["\\\r\n] | ESCAPE_SEQ | EOF)*;
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
LINE_COMMENT : '--' COMMENT_CONTENT -> skip;
COMMENT : '{-' (COMMENT|.)*? '-}' -> channel(HIDDEN);

// literate mode
LITERATE_START : 'remark';

mode LITERATE_MODE;
LITERATE_END : 'finished' -> more, popMode;
