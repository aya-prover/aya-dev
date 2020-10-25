grammar Mzi;

// statements
stmt : decl
     | cmd
     ;

cmd : cmdName moduleName using? hiding?;

cmdName : '\\open'         # cmdOpen
        | '\\import'       # cmdImport
        ;

using : '\\using'? '(' id_list ')';
hiding : '\\hiding' '(' id_list ')';

moduleName : ID ('.' ID)*;

// declarations

decl : operatorDecl
     | fnDecl
     | classDecl
     | dataDecl
     ;

associativity : '\\infix'               # nonAssocInfix
              | '\\infixl'              # leftAssocInfix
              | '\\infixr'              # rightAssocInfix
              | '\\fix'                 # nonAssoc
              | '\\fixl'                # leftAssoc
              | '\\fixr'                # rightAssoc
              ;
operatorDecl : associativity NUMBER ID;

fnDecl : '\\def' fnModifiers* ID tele* (':' expr)? fnBody;
fnBody : rightEqArrow expr;
fnModifiers : '\\erased'                # fnErased
            ;

classDecl : '\\structure' ID fieldTele* ('\\extends' id_list)? ('|' classFieldOrImpl)*;

fieldTele : '(' '\\coerce'? ID+ ':' expr ')'        # explicitFieldTele
          | '{' '\\coerce'? ID+ ':' expr '}'        # implicitFieldTele
          ;

classFieldOrImpl : classFieldDef    # classField
                 | classImplDef     # classImpl
                 ;

classFieldDef : '\\coerce'? ID tele* ':' expr;

classImplDef : ID tele* rightEqArrow expr;

dataDecl : '\\data' ID tele* (':' expr)? dataBody;

dataBody : ('|' ctor)*                               # dataCtors
         | elim ctorClause*                          # dataClauses
         ;

// TODO[imkiva]: some code commented in Arend.g4
ctor : '\\coerce'? ID tele* (elim? '{' clause? ('|' clause)* '}')?;

elim : '\\elim' ID (',' ID)*;

ctorClause : '|' pattern rightEqArrow ctor;

// expressions

sigmaKw : '\\Sigma'
        | 'Σ'
        ;

lambdaKw : '\\lam'
         | 'λ'
         ;

expr : appExpr                                                           # app
     | <assoc=right> expr rightArrow expr                                      # arr
     | <assoc=right> expr '.' NUMBER                                     # proj
     | '\\Pi' tele+ rightArrow expr                                            # pi
     | sigmaKw tele*                                                     # sigma
     | lambdaKw tele+ (rightEqArrow expr?)?                                      # lam
     | '\\matchy' expr? ( '|' clause)*                                   # matchy
     ;

appExpr : atom argument*      # appArg
        | UNIVERSE            # appUniverse
        | SET                 # appSetUniverse
        ;

tupleExpr : expr (':' expr)?;

atom : literal                                     # atomLiteral
     | '(' (tupleExpr (',' tupleExpr)* ','?)? ')'  # tuple
     | NUMBER                                      # atomNumber
     | STRING                                      # atomString
     ;

argument : expr                                     # argumentExplicit
         | universeAtom                             # argumentUniverse
         | '{' tupleExpr (',' tupleExpr)* ','? '}'  # argumentImplicit
         ;

clause : pattern rightEqArrow expr;

pattern : atomPattern ('\\as' ID (':' expr)?)?          # patAtom
        | ID atomPatternOrID* ('\\as' ID)? (':' expr)?  # patCtor
        ;

atomPattern : '(' (pattern (',' pattern)*)? ')'   # atomPatExplicit
            | '{' pattern '}'                     # atomPatImplicit
            | NUMBER                              # atomPatNumbers
            | '_'                                 # atomPatWildcard
            ;

atomPatternOrID : atomPattern     # patternOrIDAtom
                | ID              # patternID
                ;

literal : ID ('.' (INFIX | POSTFIX))?       # name
        | '\\Prop'                          # prop
        | '_'                               # unknown
        | INFIX                             # infix
        | POSTFIX                           # postfix
        | '{?' ID? ('(' expr? ')')? '}'     # goal
        ;

universeAtom : TRUNCATED_UNIVERSE       # uniTruncatedUniverse
             | UNIVERSE                 # uniUniverse
             | SET                      # uniSetUniverse
             ;

tele : literal                          # teleLiteral
     | universeAtom                     # teleUniverse
     | '(' typedExpr ')'                # explicit
     | '{' typedExpr '}'                # implicit
     ;

typedExpr : expr (':' expr)? ;

// utilities
id_list : (ID ',')* ID?;
rightArrow : '->' | '→';
rightEqArrow : '=>' | '⇒';

// operators
INFIX : '`' ID '`';
POSTFIX : '`' ID;

// universe
UNIVERSE : '\\Type' [0-9]*;
TRUNCATED_UNIVERSE : '\\' (NUMBER '-' | 'oo-' | 'h') 'Type' [0-9]*;
SET : '\\Set' [0-9]*;

// numbers
NUMBER : [0-9]+;

// string
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
