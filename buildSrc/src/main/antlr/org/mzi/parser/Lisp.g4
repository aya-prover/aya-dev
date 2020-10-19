grammar Lisp;

expr : atom EOF
     | '(' IDENT expr* ')' EOF
     ;

atom : NUMBER
     | IDENT
     ;

NUMBER : [0-9]+;
IDENT : [a-zA-Z_][a-zA-Z_'0-9#/=+*-]*;
