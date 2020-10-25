grammar Mzi;

// declarations

associativity : '\\infix'               # nonAssocInfix
              | '\\infixl'              # leftAssocInfix
              | '\\infixr'              # rightAssocInfix
              | '\\fix'                 # nonAssoc
              | '\\fixl'                # leftAssoc
              | '\\fixr'                # rightAssoc
              ;
operatorDecl : associativity NUMBER ID;

fnModifiers : '{?}';
fnDecl : '\\def' fnModifiers* ID tele* (':' expr)? '=>' fnBody;
fnBody : '{?}';

// expressions

sigmaKw : '\\Sigma'
        | 'Σ'
        ;

lambdaKw : '\\lam'
         | 'λ'
         ;

expr : appExpr                                                           # app
     | <assoc=right> expr '->' expr                                      # arr
     | <assoc=right> expr '.' NUMBER                                     # proj
     | '\\Pi' tele+ '->' expr                                            # pi
     | sigmaKw tele*                                                     # sigma
     | lambdaKw tele+ ('=>' expr?)?                                      # lam
     | '\\matchy' expr? ( '|' matchyClause)*                             # matchy
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

matchyClause : pattern '=>' expr;

pattern : '{?}';

longName : ID ('.' ID)*;

literal : longName ('.' (INFIX | POSTFIX))? # name
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
