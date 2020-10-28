// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
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
