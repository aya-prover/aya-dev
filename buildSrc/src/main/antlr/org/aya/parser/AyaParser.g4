// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE file.
parser grammar AyaParser;

options { tokenVocab = AyaLexer; }

program : stmt* EOF;

repl : stmt+ | REPL_COMMAND? expr ;

// statements
stmt : decl
     | importCmd
     | openCmd
     | module
     | remark
     | levels
     | generalize
     | sample
     ;

sample : (EXAMPLE | COUNTEREXAMPLE) decl ;
remark : DOC_COMMENT+;

importCmd : IMPORT qualifiedId (AS ID)?;
openCmd : PUBLIC? OPEN IMPORT? qualifiedId useHide?;
module : MODULE_KW ID LBRACE stmt* RBRACE;

useHide : (USING | HIDING) useHideList+;
useHideList : LPAREN idsComma RPAREN;

levels : ULEVEL ids ;
generalize : VARIABLE ids type ;

// declarations

decl : PRIVATE?
     ( fnDecl
     | structDecl
     | dataDecl
     | primDecl
     );

assoc : INFIX | INFIXL | INFIXR;

declNameOrInfix : ID | assoc ID;

bindBlock : BIND_KW (TIGHTER | LOOSER) qIdsComma
          | BIND_KW LBRACE (tighters | loosers)* RBRACE ;
tighters : TIGHTER qIdsComma;
loosers : LOOSER qIdsComma;

fnDecl : DEF fnModifiers* declNameOrInfix tele* type? fnBody bindBlock?;

fnBody : IMPLIES expr
       | (BAR clause)* ;

fnModifiers : OPAQUE
            | INLINE
            | OVERLAP
            | PATTERN_KW
            ;

structDecl : STRUCT declNameOrInfix tele* type? (EXTENDS idsComma)? (BAR field)* bindBlock?;

primDecl : PRIM assoc? ID tele* type? ;

field : COERCE? declNameOrInfix tele* type clauses? bindBlock? # fieldDecl
      | declNameOrInfix tele* type? IMPLIES expr    bindBlock? # fieldImpl
      ;

dataDecl : (PUBLIC? OPEN)? DATA declNameOrInfix tele* type? dataBody* bindBlock?;

dataBody : (BAR dataCtor)       # dataCtors
         | dataCtorClause       # dataClauses
         ;

dataCtor : COERCE? declNameOrInfix tele* clauses? bindBlock?;

dataCtorClause : BAR patterns IMPLIES dataCtor;

// expressions
expr : atom                            # single
     | expr argument+                  # app
     | NEW_KW expr LBRACE newArg* RBRACE # new
     | <assoc=right> expr TO expr      # arr
     | expr projFix                    # proj
     | LSUC_KW atom                    # lsuc
     | LMAX_KW atom+                   # lmax
     | PI tele+ TO expr                # pi
     | FORALL tele+ TO expr            # forall
     | SIGMA tele+ SUCHTHAT expr       # sigma
     | LAMBDA tele+ (IMPLIES expr?)?   # lam
     | MATCH exprList clauses          # match
     ;

newArg : BAR ID ids IMPLIES expr;

atom : literal
     | LPAREN exprList RPAREN
     ;

argument : atom projFix*
         | LBRACE exprList RBRACE
         | LBRACE ID IMPLIES expr? RBRACE
         | LBRACE ULEVEL exprList RBRACE
         ;

projFix : DOT (NUMBER | ID);

clauses : LBRACE clause? (BAR clause)* RBRACE ;
clause : patterns (IMPLIES expr)? ;

patterns : pattern (COMMA pattern)* ;
pattern : atomPatterns
        ;

atomPatterns : atomPattern+ ;
atomPattern : LPAREN patterns RPAREN (AS ID)?
            | LBRACE patterns RBRACE (AS ID)?
            | NUMBER
            | ABSURD
            | ID
            | CALM_FACE
            ;

literal : qualifiedId
        | CALM_FACE
        | LGOAL expr? RGOAL
        | NUMBER
        | STRING
        | TYPE
        ;

tele : literal
     | LPAREN teleBinder RPAREN
     | LBRACE teleMaybeTypedExpr RBRACE
     ;

// Explicit arguments may be anonymous
teleBinder : expr
           | teleMaybeTypedExpr ;

teleMaybeTypedExpr : PATTERN_KW? ids type?;

// utilities
exprList : (expr COMMA)* expr?;
idsComma : (ID COMMA)* ID?;
qIdsComma : (qualifiedId COMMA)* qualifiedId?;
ids : ID*;
type : COLON expr;

qualifiedId : ID (COLON2 ID)*;
