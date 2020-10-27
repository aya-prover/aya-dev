// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
grammar Mzi;

program : stmt* EOF;

// statements
stmt : decl
     | cmd
     ;

cmd : (OPEN | IMPORT) moduleName useHide*;
useHide : (USING | HIDING) '(' ids ')';

moduleName : ID ('.' ID)*;

// declarations

decl : fnDecl
     | structDecl
     | dataDecl
     ;

assoc : INFIX
      | INFIXL
      | INFIXR
      | FIX
      | FIXL
      | FIXR
      | TWIN
      ;

abuse : '\\abusing' ('{' stmt* '}' | stmt);

fnDecl : '\\def' fnModifiers* assoc? ID tele* type? fnBody abuse?;

fnBody : rightEqArrow expr;

fnModifiers : ERASE
            | INLINE
            ;

structDecl : '\\structure' ID fieldTele* ('\\extends' ids)? ('|' field)* abuse?;

fieldTeleInner : COERCE? ID+ type;
fieldTele : '(' fieldTeleInner ')'        # explicitFieldTele
          | '{' fieldTeleInner '}'        # implicitFieldTele
          ;

field : COERCE? ID tele* type        # fieldDecl
      | ID tele* rightEqArrow expr   # fieldImpl
      ;

dataDecl : '\\data' ID tele* type? dataBody abuse?;

dataBody : ('|' ctor)*       # dataCtors
         | elim ctorClause*  # dataClauses
         ;

// TODO[imkiva]: some code commented in Arend.g4
ctor : COERCE? ID tele* (elim? '{' clause? ('|' clause)* '}')?;

elim : '\\elim' ID (',' ID)*;

ctorClause : '|' pattern rightEqArrow ctor;

// expressions
sigmaKw : '\\Sig' | 'Σ';
lambdaKw : '\\lam' | 'λ';
piKw : '\\Pi' | 'Π';
matchKw : '\\matchy';

expr : atom argument*                                         # app
     | <assoc=right> expr rightArrow expr                     # arr
     | <assoc=right> expr '.' NUMBER                          # proj
     | piKw tele+ rightArrow expr                             # pi
     | sigmaKw tele*                                          # sigma
     | lambdaKw tele+ (rightEqArrow expr?)?                   # lam
     | matchKw matchArg (',' matchArg)* ( '|' clause)*        # match
     ;

matchArg : elim
         | expr
         ;

typed : expr type? ;

atom : literal
     | '(' (typed ',')? typed? ')'
     ;

argument : expr
         | '{' (typed ',')? typed? '}'
         ;

clause : patterns rightEqArrow expr;

patterns : pattern (',' pattern)* ;
pattern : atomPattern ('\\as' ID type?)?             # patAtom
        | ID (atomPattern | ID)* ('\\as' ID)? type?  # patCtor
        ;

atomPattern : '(' patterns? ')' # atomPatExplicit
            | '{' patterns '}'  # atomPatImplicit
            | NUMBER            # atomPatNumber
            | CALM_FACE         # atomPatWildcard
            ;

literal : ID ('.' idFix)?
        | PROP
        | CALM_FACE
        | idFix
        | '{?' expr? '?}'
        | NUMBER
        | STRING
        | UNIVERSE
        | SET
        ;

tele : literal                # teleLiteral
     | '(' teleTypedExpr ')'  # explicit
     | '{' teleTypedExpr '}'  # implicit
     ;

teleTypedExpr : expr type?;

// utilities
ids : (ID ',')* ID?;
rightArrow : '->' | '→';
rightEqArrow : '=>' | '⇒';
type : ':' expr;

// operators
idFix : INFIX | POSTFIX;
INFIX : '`' ID '`';
POSTFIX : '`' ID;

// associativities
INFIXN : '\\infix';
INFIXL : '\\infixl';
INFIXR : '\\infixr';
FIX : '\\fix';
FIXL : '\\fixl';
FIXR : '\\fixr';
TWIN : '\\twin';

// universe
UNIVERSE : '\\' (NUMBER '-' | 'oo-' | 'h')? 'Type' [0-9]*;
SET : '\\Set' [0-9]*;
PROP : '\\Prop';

// other keywords
OPEN : '\\open';
IMPORT : '\\import';
USING : '\\using';
HIDING : '\\hiding';
COERCE : '\\coerce';
ERASE : '\\erase';
INLINE : '\\inline';

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
fragment START_CHAR : [~!@#$%^&*\-+=<>?/|:[\u005Da-zA-Z_\u2200-\u22FF];
ID : START_CHAR (START_CHAR | [0-9'])*;

// whitespaces
WS : [ \t\r\n]+ -> channel(HIDDEN);
LINE_COMMENT : '--' '-'* (~[~!@#$%^&*\-+=<>?/|:[\u005Da-zA-Z_0-9'\u2200-\u22FF\r\n] ~[\r\n]* | ) -> skip;
COMMENT : '{-' (COMMENT|.)*? '-}' -> channel(HIDDEN);
