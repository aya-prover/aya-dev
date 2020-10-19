grammar Lisp;

expr : '(' IDENT expr* ')'
     | atom
     ;

atom : NUMBER
     | IDENT
     ;

WS : [ \t\r\n]+ -> channel(HIDDEN);
NUMBER : [0-9]+;
IDENT : [a-zA-Z_][a-zA-Z_'0-9#/=+*-]*;
