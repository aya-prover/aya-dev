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
     | generalize
     ;

remark : DOC_COMMENT+;

importCmd : IMPORT qualifiedId (AS weakId)?;
openCmd : PUBLIC? OPEN IMPORT? qualifiedId useHide?;
module : MODULE_KW weakId LBRACE stmt* RBRACE;

useHide : USING useList+ | HIDING hideList+;
hideList : LPAREN idsComma RPAREN;
useList : LPAREN useIdsComma RPAREN;
useIdsComma : (useId COMMA)* useId?;
useId : weakId useAs?;
useAs : AS assoc? weakId bindBlock?;

generalize : VARIABLE ids type ;

// declarations

sampleModifiers : EXAMPLE | COUNTEREXAMPLE;

decl : PRIVATE?
     ( fnDecl
     | structDecl
     | dataDecl
     | primDecl
     );

assoc : INFIX | INFIXL | INFIXR;

declNameOrInfix : weakId | assoc weakId;

bindBlock : BIND_KW (TIGHTER | LOOSER) qIdsComma
          | BIND_KW LBRACE (tighters | loosers)* RBRACE ;
tighters : TIGHTER qIdsComma;
loosers : LOOSER qIdsComma;

fnDecl : sampleModifiers? DEF fnModifiers* declNameOrInfix tele* type? fnBody bindBlock?;

fnBody : IMPLIES expr
       | (BAR clause)* ;

fnModifiers : OPAQUE
            | INLINE
            | OVERLAP
            | PATTERN_KW
            ;

structDecl : sampleModifiers? (PUBLIC? OPEN)? STRUCT declNameOrInfix tele* type? (EXTENDS idsComma)? (BAR field)* bindBlock?;

primDecl : PRIM weakId tele* type? ;

field : COERCE? declNameOrInfix tele* type clauses? bindBlock? # fieldDecl
      | declNameOrInfix tele* type? IMPLIES expr    bindBlock? # fieldImpl
      ;

dataDecl : sampleModifiers? (PUBLIC? OPEN)? DATA declNameOrInfix tele* type? dataBody* bindBlock?;

dataBody : (BAR dataCtor)       # dataCtors
         | dataCtorClause       # dataClauses
         ;

dataCtor : COERCE? declNameOrInfix tele* clauses? bindBlock?;

dataCtorClause : BAR patterns IMPLIES dataCtor;

// expressions
expr : atom                                 # single
     | expr argument+                       # app
     | NEW_KW expr newBody?                 # new
     | <assoc=right> expr TO expr           # arr
     | expr projFix                         # proj
     | PI tele+ TO expr                     # pi
     | FORALL tele+ TO expr                 # forall
     | SIGMA tele+ SUCHTHAT expr            # sigma
     | LAMBDA tele+ (IMPLIES expr?)?        # lam
     | MATCH exprList clauses               # match
     | DO_KW LBRACE? doBlock RBRACE?        # do
     | LIDIOM idiomBlock? RIDIOM            # idiom
     | LARRAY arrayBlock? RARRAY            # array
     ;

arrayBlock : exprList | expr BAR listComp;

listComp : (doBindingExpr COMMA)* doBindingExpr;

idiomBlock : barredExpr* expr+;

doBlock : (doBlockExpr COMMA)* doBlockExpr;

doBlockExpr : doBindingExpr | expr;

newArg : BAR weakId ids IMPLIES expr;
// New body new body but you!
newBody : LBRACE newArg* RBRACE;

// ulift is written here because we want `x ulift + y` to work
atom : ULIFT* literal
     | LPAREN exprList RPAREN
     ;

argument : atom projFix*
         | LBRACE exprList RBRACE
         | LBRACE weakId DEFINE_AS expr? RBRACE
         ;

projFix : DOT (NUMBER | qualifiedId);

clauses : LBRACE clause? (BAR clause)* RBRACE ;
clause : patterns (IMPLIES expr)? ;

patterns : pattern (COMMA pattern)* ;
pattern : atomPatterns
        ;

atomPatterns : atomPattern+ ;
atomPattern : LPAREN patterns RPAREN (AS weakId)?
            | LBRACE patterns RBRACE (AS weakId)?
            | NUMBER
            | LPAREN RPAREN
            | weakId
            | CALM_FACE
            ;

literal : qualifiedId
        | CALM_FACE
        | LGOAL expr? RGOAL
        | NUMBER
        | STRING
        | TYPE
        | I
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
exprList : (expr COMMA)* expr;
barredExpr : expr+ BAR;
idsComma : (weakId COMMA)* weakId?;
qIdsComma : (qualifiedId COMMA)* qualifiedId?;
ids : weakId*;
type : COLON expr;
doBindingExpr : weakId LARROW expr;

qualifiedId : weakId (COLON2 weakId)*;
weakId : ID | REPL_COMMAND;
