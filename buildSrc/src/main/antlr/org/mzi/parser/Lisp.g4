grammar Lisp;

expr : atom
     | '(' IDENT expr* ')' EOF
     ;

atom : NUMBER
     | IDENT
     ;

NUMBER : [0-9]+;
IDENT : [a-zA-Z_][a-zA-Z_'0-9#/=+*-]*;
