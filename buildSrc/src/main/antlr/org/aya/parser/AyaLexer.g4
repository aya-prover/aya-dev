lexer grammar AyaLexer;

INFIX : '`' ID '`';
POSTFIX : '`' ID;

// bind
TIGHTER : 'tighter';
LOOSER : 'looser';
OP_APP : 'application';

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
HTYPE : 'hType';
UTYPE : 'uType';
ULEVEL : 'ulevel';
HLEVEL : 'hlevel';
TYPE : 'Type';
INF_TYPE : 'ooType';
SET_UNIV : 'Set';
PROP : 'Prop';

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
fragment AYA_SIMPLE_LETTER : [~!@#$%^&*\-+=<>?/|[\u005Da-zA-Z_\u2200-\u22FF];
fragment AYA_UNICODE : [\u0080-\uFEFE] | [\uFF00-\u{10FFFF}]; // exclude U+FEFF which is a truly invisible char
fragment AYA_LETTER : AYA_SIMPLE_LETTER | AYA_UNICODE;
ID : AYA_LETTER (AYA_LETTER | [0-9'])*;

// whitespaces
WS : [ \t\r\n]+ -> channel(HIDDEN);
LINE_COMMENT : '--' '-'* (~[~!@#$%^&*\-+=<>?/|:[\u005Da-zA-Z_0-9'\u2200-\u22FF\r\n] ~[\r\n]* | ) -> skip;
COMMENT : '{-' (COMMENT|.)*? '-}' -> channel(HIDDEN);

// literate mode
LITERATE_START : 'remark';

mode LITERATE_MODE;
LITERATE_END : 'finished' -> more, popMode;
