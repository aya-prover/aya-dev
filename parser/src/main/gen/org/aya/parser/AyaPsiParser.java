// This is a generated file. Not intended for manual editing.
package org.aya.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static org.aya.parser.AyaPsiElementTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class AyaPsiParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, EXTENDS_SETS_);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    r = parse_root_(t, b);
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b) {
    return parse_root_(t, b, 0);
  }

  static boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return program(b, l + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(ARRAY_BLOCK, ARRAY_COMP_BLOCK, ARRAY_ELEMENTS_BLOCK),
    create_token_set_(ATOM_BIND_PATTERN, ATOM_LIST_PATTERN, ATOM_PATTERN),
    create_token_set_(ARGUMENT, ATOM_EX_ARGUMENT, NAMED_IM_ARGUMENT, TUPLE_IM_ARGUMENT),
    create_token_set_(DATA_DECL, DECL, FN_DECL, GENERALIZE,
      IMPORT_CMD, MODULE, OPEN_CMD, PRIM_DECL,
      REMARK, STMT, STRUCT_DECL),
    create_token_set_(APP_EXPR, ARRAY_ATOM, ARROW_EXPR, ATOM_EXPR,
      CALM_FACE_EXPR, DO_EXPR, EXPR, FORALL_EXPR,
      GOAL_EXPR, HOLE_EXPR, IDIOM_ATOM, LAMBDA_EXPR,
      LET_EXPR, LITERAL, LIT_INT_EXPR, LIT_STRING_EXPR,
      MATCH_EXPR, NEW_EXPR, PARTIAL_EXPR, PATH_EXPR,
      PI_EXPR, PROJ_EXPR, REF_EXPR, SIGMA_EXPR,
      THIS_EXPR, TUPLE_ATOM, ULIFT_ATOM, UNIV_EXPR),
  };

  /* ********************************************************** */
  // atomExArgument
  //            | tupleImArgument
  //            | namedImArgument
  public static boolean argument(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "argument")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, ARGUMENT, "<argument>");
    r = atomExArgument(b, l + 1);
    if (!r) r = tupleImArgument(b, l + 1);
    if (!r) r = namedImArgument(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // LARRAY arrayBlock? RARRAY
  public static boolean arrayAtom(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arrayAtom")) return false;
    if (!nextTokenIs(b, LARRAY)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ARRAY_ATOM, null);
    r = consumeToken(b, LARRAY);
    p = r; // pin = 1
    r = r && report_error_(b, arrayAtom_1(b, l + 1));
    r = p && consumeToken(b, RARRAY) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // arrayBlock?
  private static boolean arrayAtom_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arrayAtom_1")) return false;
    arrayBlock(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // arrayCompBlock | arrayElementsBlock
  public static boolean arrayBlock(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arrayBlock")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, ARRAY_BLOCK, "<array block>");
    r = arrayCompBlock(b, l + 1);
    if (!r) r = arrayElementsBlock(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // expr BAR listComp
  public static boolean arrayCompBlock(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arrayCompBlock")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ARRAY_COMP_BLOCK, "<array comp block>");
    r = expr(b, l + 1, -1);
    r = r && consumeToken(b, BAR);
    r = r && listComp(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // exprList
  public static boolean arrayElementsBlock(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arrayElementsBlock")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ARRAY_ELEMENTS_BLOCK, "<array elements block>");
    r = exprList(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // KW_INFIX | KW_INFIXL | KW_INFIXR | KW_FIXL | KW_FIXR
  public static boolean assoc(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "assoc")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ASSOC, "<assoc>");
    r = consumeToken(b, KW_INFIX);
    if (!r) r = consumeToken(b, KW_INFIXL);
    if (!r) r = consumeToken(b, KW_INFIXR);
    if (!r) r = consumeToken(b, KW_FIXL);
    if (!r) r = consumeToken(b, KW_FIXR);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // weakId
  public static boolean atomBindPattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "atomBindPattern")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = weakId(b, l + 1);
    exit_section_(b, m, ATOM_BIND_PATTERN, r);
    return r;
  }

  /* ********************************************************** */
  // atomExpr projFix*
  public static boolean atomExArgument(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "atomExArgument")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ATOM_EX_ARGUMENT, "<atom ex argument>");
    r = atomExpr(b, l + 1);
    r = r && atomExArgument_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // projFix*
  private static boolean atomExArgument_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "atomExArgument_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!projFix(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "atomExArgument_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // LARRAY patterns? RARRAY (KW_AS weakId)?
  public static boolean atomListPattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "atomListPattern")) return false;
    if (!nextTokenIs(b, LARRAY)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LARRAY);
    r = r && atomListPattern_1(b, l + 1);
    r = r && consumeToken(b, RARRAY);
    r = r && atomListPattern_3(b, l + 1);
    exit_section_(b, m, ATOM_LIST_PATTERN, r);
    return r;
  }

  // patterns?
  private static boolean atomListPattern_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "atomListPattern_1")) return false;
    patterns(b, l + 1);
    return true;
  }

  // (KW_AS weakId)?
  private static boolean atomListPattern_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "atomListPattern_3")) return false;
    atomListPattern_3_0(b, l + 1);
    return true;
  }

  // KW_AS weakId
  private static boolean atomListPattern_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "atomListPattern_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_AS);
    r = r && weakId(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // atomListPattern
  //               | NUMBER
  //               | LPAREN RPAREN
  //               | atomBindPattern
  //               | CALM_FACE
  public static boolean atomPattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "atomPattern")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, ATOM_PATTERN, "<atom pattern>");
    r = atomListPattern(b, l + 1);
    if (!r) r = consumeToken(b, NUMBER);
    if (!r) r = parseTokens(b, 0, LPAREN, RPAREN);
    if (!r) r = atomBindPattern(b, l + 1);
    if (!r) r = consumeToken(b, CALM_FACE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // clause
  public static boolean bareClause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bareClause")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BARE_CLAUSE, "<bare clause>");
    r = clause(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // subSystem
  public static boolean bareSubSystem(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bareSubSystem")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BARE_SUB_SYSTEM, "<bare sub system>");
    r = subSystem(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // expr BAR
  public static boolean barred(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "barred")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BARRED, "<barred>");
    r = expr(b, l + 1, -1);
    r = r && consumeToken(b, BAR);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // BAR clause
  public static boolean barredClause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "barredClause")) return false;
    if (!nextTokenIs(b, BAR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, BAR);
    r = r && clause(b, l + 1);
    exit_section_(b, m, BARRED_CLAUSE, r);
    return r;
  }

  /* ********************************************************** */
  // BAR subSystem
  public static boolean barredSubSystem(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "barredSubSystem")) return false;
    if (!nextTokenIs(b, BAR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, BAR);
    r = r && subSystem(b, l + 1);
    exit_section_(b, m, BARRED_SUB_SYSTEM, r);
    return r;
  }

  /* ********************************************************** */
  // (tighters | loosers)+
  public static boolean bindBlock(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bindBlock")) return false;
    if (!nextTokenIs(b, "<bind block>", KW_LOOSER, KW_TIGHTER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, BIND_BLOCK, "<bind block>");
    r = bindBlock_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!bindBlock_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "bindBlock", c)) break;
    }
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // tighters | loosers
  private static boolean bindBlock_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bindBlock_0")) return false;
    boolean r;
    r = tighters(b, l + 1);
    if (!r) r = loosers(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // CALM_FACE
  public static boolean calmFaceExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "calmFaceExpr")) return false;
    if (!nextTokenIs(b, CALM_FACE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, CALM_FACE);
    exit_section_(b, m, CALM_FACE_EXPR, r);
    return r;
  }

  /* ********************************************************** */
  // patterns (IMPLIES expr)?
  public static boolean clause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "clause")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CLAUSE, "<clause>");
    r = patterns(b, l + 1);
    r = r && clause_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (IMPLIES expr)?
  private static boolean clause_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "clause_1")) return false;
    clause_1_0(b, l + 1);
    return true;
  }

  // IMPLIES expr
  private static boolean clause_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "clause_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IMPLIES);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // LBRACE bareClause? barredClause* RBRACE
  public static boolean clauses(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "clauses")) return false;
    if (!nextTokenIs(b, LBRACE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACE);
    r = r && clauses_1(b, l + 1);
    r = r && clauses_2(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, CLAUSES, r);
    return r;
  }

  // bareClause?
  private static boolean clauses_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "clauses_1")) return false;
    bareClause(b, l + 1);
    return true;
  }

  // barredClause*
  private static boolean clauses_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "clauses_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!barredClause(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "clauses_2", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // <<param>> (',' <<param>>) *
  static boolean commaSep(PsiBuilder b, int l, Parser _param) {
    if (!recursion_guard_(b, l, "commaSep")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = _param.parse(b, l);
    r = r && commaSep_1(b, l + 1, _param);
    exit_section_(b, m, null, r);
    return r;
  }

  // (',' <<param>>) *
  private static boolean commaSep_1(PsiBuilder b, int l, Parser _param) {
    if (!recursion_guard_(b, l, "commaSep_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!commaSep_1_0(b, l + 1, _param)) break;
      if (!empty_element_parsed_guard_(b, "commaSep_1", c)) break;
    }
    return true;
  }

  // ',' <<param>>
  private static boolean commaSep_1_0(PsiBuilder b, int l, Parser _param) {
    if (!recursion_guard_(b, l, "commaSep_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && _param.parse(b, l);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // dataCtorClause
  //            | (BAR dataCtor)
  public static boolean dataBody(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataBody")) return false;
    if (!nextTokenIs(b, BAR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = dataCtorClause(b, l + 1);
    if (!r) r = dataBody_1(b, l + 1);
    exit_section_(b, m, DATA_BODY, r);
    return r;
  }

  // BAR dataCtor
  private static boolean dataBody_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataBody_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, BAR);
    r = r && dataCtor(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // KW_COERCE? declNameOrInfix tele* partialBlock? bindBlock?
  public static boolean dataCtor(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataCtor")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, DATA_CTOR, "<data ctor>");
    r = dataCtor_0(b, l + 1);
    r = r && declNameOrInfix(b, l + 1);
    r = r && dataCtor_2(b, l + 1);
    r = r && dataCtor_3(b, l + 1);
    r = r && dataCtor_4(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // KW_COERCE?
  private static boolean dataCtor_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataCtor_0")) return false;
    consumeToken(b, KW_COERCE);
    return true;
  }

  // tele*
  private static boolean dataCtor_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataCtor_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!tele(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "dataCtor_2", c)) break;
    }
    return true;
  }

  // partialBlock?
  private static boolean dataCtor_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataCtor_3")) return false;
    partialBlock(b, l + 1);
    return true;
  }

  // bindBlock?
  private static boolean dataCtor_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataCtor_4")) return false;
    bindBlock(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // BAR patterns IMPLIES dataCtor
  public static boolean dataCtorClause(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataCtorClause")) return false;
    if (!nextTokenIs(b, BAR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, BAR);
    r = r && patterns(b, l + 1);
    r = r && consumeToken(b, IMPLIES);
    r = r && dataCtor(b, l + 1);
    exit_section_(b, m, DATA_CTOR_CLAUSE, r);
    return r;
  }

  /* ********************************************************** */
  // sampleModifiers? (KW_PUBLIC? openKw)? KW_DATA declNameOrInfix tele* type? dataBody* bindBlock?
  public static boolean dataDecl(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataDecl")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, DATA_DECL, "<data decl>");
    r = dataDecl_0(b, l + 1);
    r = r && dataDecl_1(b, l + 1);
    r = r && consumeToken(b, KW_DATA);
    r = r && declNameOrInfix(b, l + 1);
    r = r && dataDecl_4(b, l + 1);
    r = r && dataDecl_5(b, l + 1);
    r = r && dataDecl_6(b, l + 1);
    r = r && dataDecl_7(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // sampleModifiers?
  private static boolean dataDecl_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataDecl_0")) return false;
    sampleModifiers(b, l + 1);
    return true;
  }

  // (KW_PUBLIC? openKw)?
  private static boolean dataDecl_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataDecl_1")) return false;
    dataDecl_1_0(b, l + 1);
    return true;
  }

  // KW_PUBLIC? openKw
  private static boolean dataDecl_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataDecl_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = dataDecl_1_0_0(b, l + 1);
    r = r && openKw(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // KW_PUBLIC?
  private static boolean dataDecl_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataDecl_1_0_0")) return false;
    consumeToken(b, KW_PUBLIC);
    return true;
  }

  // tele*
  private static boolean dataDecl_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataDecl_4")) return false;
    while (true) {
      int c = current_position_(b);
      if (!tele(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "dataDecl_4", c)) break;
    }
    return true;
  }

  // type?
  private static boolean dataDecl_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataDecl_5")) return false;
    type(b, l + 1);
    return true;
  }

  // dataBody*
  private static boolean dataDecl_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataDecl_6")) return false;
    while (true) {
      int c = current_position_(b);
      if (!dataBody(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "dataDecl_6", c)) break;
    }
    return true;
  }

  // bindBlock?
  private static boolean dataDecl_7(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dataDecl_7")) return false;
    bindBlock(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // KW_PRIVATE?
  //        ( fnDecl
  //        | structDecl
  //        | dataDecl
  //        | primDecl
  //        )
  public static boolean decl(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "decl")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, DECL, "<decl>");
    r = decl_0(b, l + 1);
    r = r && decl_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // KW_PRIVATE?
  private static boolean decl_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "decl_0")) return false;
    consumeToken(b, KW_PRIVATE);
    return true;
  }

  // fnDecl
  //        | structDecl
  //        | dataDecl
  //        | primDecl
  private static boolean decl_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "decl_1")) return false;
    boolean r;
    r = fnDecl(b, l + 1);
    if (!r) r = structDecl(b, l + 1);
    if (!r) r = dataDecl(b, l + 1);
    if (!r) r = primDecl(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // weakId | assoc weakId
  public static boolean declNameOrInfix(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "declNameOrInfix")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, DECL_NAME_OR_INFIX, "<decl name or infix>");
    r = weakId(b, l + 1);
    if (!r) r = declNameOrInfix_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // assoc weakId
  private static boolean declNameOrInfix_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "declNameOrInfix_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = assoc(b, l + 1);
    r = r && weakId(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // weakId LARROW expr
  public static boolean doBinding(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "doBinding")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = weakId(b, l + 1);
    r = r && consumeToken(b, LARROW);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, DO_BINDING, r);
    return r;
  }

  /* ********************************************************** */
  // <<commaSep doBlockContent>>
  public static boolean doBlock(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "doBlock")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, DO_BLOCK, "<do block>");
    r = commaSep(b, l + 1, AyaPsiParser::doBlockContent);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // doBinding | expr
  public static boolean doBlockContent(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "doBlockContent")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, DO_BLOCK_CONTENT, "<do block content>");
    r = doBinding(b, l + 1);
    if (!r) r = expr(b, l + 1, -1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // <<commaSep expr>>
  public static boolean exprList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "exprList")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, EXPR_LIST, "<expr list>");
    r = commaSep(b, l + 1, expr_parser_);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // IMPLIES expr
  //          | barredClause*
  public static boolean fnBody(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fnBody")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FN_BODY, "<fn body>");
    r = fnBody_0(b, l + 1);
    if (!r) r = fnBody_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // IMPLIES expr
  private static boolean fnBody_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fnBody_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IMPLIES);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  // barredClause*
  private static boolean fnBody_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fnBody_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!barredClause(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "fnBody_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // sampleModifiers? KW_DEF fnModifiers* declNameOrInfix tele* type? fnBody bindBlock?
  public static boolean fnDecl(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fnDecl")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FN_DECL, "<fn decl>");
    r = fnDecl_0(b, l + 1);
    r = r && consumeToken(b, KW_DEF);
    r = r && fnDecl_2(b, l + 1);
    r = r && declNameOrInfix(b, l + 1);
    r = r && fnDecl_4(b, l + 1);
    r = r && fnDecl_5(b, l + 1);
    r = r && fnBody(b, l + 1);
    r = r && fnDecl_7(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // sampleModifiers?
  private static boolean fnDecl_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fnDecl_0")) return false;
    sampleModifiers(b, l + 1);
    return true;
  }

  // fnModifiers*
  private static boolean fnDecl_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fnDecl_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!fnModifiers(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "fnDecl_2", c)) break;
    }
    return true;
  }

  // tele*
  private static boolean fnDecl_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fnDecl_4")) return false;
    while (true) {
      int c = current_position_(b);
      if (!tele(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "fnDecl_4", c)) break;
    }
    return true;
  }

  // type?
  private static boolean fnDecl_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fnDecl_5")) return false;
    type(b, l + 1);
    return true;
  }

  // bindBlock?
  private static boolean fnDecl_7(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fnDecl_7")) return false;
    bindBlock(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // KW_OPAQUE
  //               | KW_INLINE
  //               | KW_OVERLAP
  public static boolean fnModifiers(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "fnModifiers")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FN_MODIFIERS, "<fn modifiers>");
    r = consumeToken(b, KW_OPAQUE);
    if (!r) r = consumeToken(b, KW_INLINE);
    if (!r) r = consumeToken(b, KW_OVERLAP);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // KW_VARIABLE generalizeParamName+ type
  public static boolean generalize(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generalize")) return false;
    if (!nextTokenIs(b, KW_VARIABLE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_VARIABLE);
    r = r && generalize_1(b, l + 1);
    r = r && type(b, l + 1);
    exit_section_(b, m, GENERALIZE, r);
    return r;
  }

  // generalizeParamName+
  private static boolean generalize_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generalize_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = generalizeParamName(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!generalizeParamName(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "generalize_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // teleParamName
  public static boolean generalizeParamName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "generalizeParamName")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = teleParamName(b, l + 1);
    exit_section_(b, m, GENERALIZE_PARAM_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // LGOAL expr? RGOAL
  public static boolean goalExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "goalExpr")) return false;
    if (!nextTokenIs(b, LGOAL)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LGOAL);
    r = r && goalExpr_1(b, l + 1);
    r = r && consumeToken(b, RGOAL);
    exit_section_(b, m, GOAL_EXPR, r);
    return r;
  }

  // expr?
  private static boolean goalExpr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "goalExpr_1")) return false;
    expr(b, l + 1, -1);
    return true;
  }

  /* ********************************************************** */
  // LPAREN idsComma RPAREN
  public static boolean hideList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "hideList")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && idsComma(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, HIDE_LIST, r);
    return r;
  }

  /* ********************************************************** */
  // goalExpr | calmFaceExpr
  public static boolean holeExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "holeExpr")) return false;
    if (!nextTokenIs(b, "<hole expr>", CALM_FACE, LGOAL)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, HOLE_EXPR, "<hole expr>");
    r = goalExpr(b, l + 1);
    if (!r) r = calmFaceExpr(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // LIDIOM idiomBlock? RIDIOM
  public static boolean idiomAtom(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "idiomAtom")) return false;
    if (!nextTokenIs(b, LIDIOM)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, IDIOM_ATOM, null);
    r = consumeToken(b, LIDIOM);
    p = r; // pin = 1
    r = r && report_error_(b, idiomAtom_1(b, l + 1));
    r = p && consumeToken(b, RIDIOM) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // idiomBlock?
  private static boolean idiomAtom_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "idiomAtom_1")) return false;
    idiomBlock(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // barred* expr
  public static boolean idiomBlock(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "idiomBlock")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, IDIOM_BLOCK, "<idiom block>");
    r = idiomBlock_0(b, l + 1);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // barred*
  private static boolean idiomBlock_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "idiomBlock_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!barred(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "idiomBlock_0", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // <<commaSep weakId>>?
  public static boolean idsComma(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "idsComma")) return false;
    Marker m = enter_section_(b, l, _NONE_, IDS_COMMA, "<ids comma>");
    commaSep(b, l + 1, AyaPsiParser::weakId);
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // KW_IMPORT qualifiedId (KW_AS weakId)?
  public static boolean importCmd(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "importCmd")) return false;
    if (!nextTokenIs(b, KW_IMPORT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_IMPORT);
    r = r && qualifiedId(b, l + 1);
    r = r && importCmd_2(b, l + 1);
    exit_section_(b, m, IMPORT_CMD, r);
    return r;
  }

  // (KW_AS weakId)?
  private static boolean importCmd_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "importCmd_2")) return false;
    importCmd_2_0(b, l + 1);
    return true;
  }

  // KW_AS weakId
  private static boolean importCmd_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "importCmd_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_AS);
    r = r && weakId(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // lambdaTeleLit
  //              | lambdaTeleEx
  //              | lambdaTeleIm
  public static boolean lambdaTele(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lambdaTele")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, LAMBDA_TELE, "<lambda tele>");
    r = lambdaTeleLit(b, l + 1);
    if (!r) r = lambdaTeleEx(b, l + 1);
    if (!r) r = lambdaTeleIm(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // teleBinderTyped
  //                    | lambdaTeleLit
  public static boolean lambdaTeleBinder(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lambdaTeleBinder")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = teleBinderTyped(b, l + 1);
    if (!r) r = lambdaTeleLit(b, l + 1);
    exit_section_(b, m, LAMBDA_TELE_BINDER, r);
    return r;
  }

  /* ********************************************************** */
  // LPAREN lambdaTeleBinder RPAREN
  public static boolean lambdaTeleEx(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lambdaTeleEx")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && lambdaTeleBinder(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, LAMBDA_TELE_EX, r);
    return r;
  }

  /* ********************************************************** */
  // LBRACE lambdaTeleBinder RBRACE
  public static boolean lambdaTeleIm(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lambdaTeleIm")) return false;
    if (!nextTokenIs(b, LBRACE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACE);
    r = r && lambdaTeleBinder(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, LAMBDA_TELE_IM, r);
    return r;
  }

  /* ********************************************************** */
  // teleParamName
  public static boolean lambdaTeleLit(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lambdaTeleLit")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = teleParamName(b, l + 1);
    exit_section_(b, m, LAMBDA_TELE_LIT, r);
    return r;
  }

  /* ********************************************************** */
  // weakId type? DEFINE_AS expr
  public static boolean letBind(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "letBind")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = weakId(b, l + 1);
    r = r && letBind_1(b, l + 1);
    r = r && consumeToken(b, DEFINE_AS);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, LET_BIND, r);
    return r;
  }

  // type?
  private static boolean letBind_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "letBind_1")) return false;
    type(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // <<commaSep doBinding>>
  public static boolean listComp(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "listComp")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = commaSep(b, l + 1, AyaPsiParser::doBinding);
    exit_section_(b, m, LIST_COMP, r);
    return r;
  }

  /* ********************************************************** */
  // NUMBER
  public static boolean litIntExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "litIntExpr")) return false;
    if (!nextTokenIs(b, NUMBER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NUMBER);
    exit_section_(b, m, LIT_INT_EXPR, r);
    return r;
  }

  /* ********************************************************** */
  // STRING
  public static boolean litStringExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "litStringExpr")) return false;
    if (!nextTokenIs(b, STRING)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, STRING);
    exit_section_(b, m, LIT_STRING_EXPR, r);
    return r;
  }

  /* ********************************************************** */
  // refExpr
  //           | holeExpr
  //           | litIntExpr
  //           | litStringExpr
  //           | univExpr
  public static boolean literal(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "literal")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, LITERAL, "<literal>");
    r = refExpr(b, l + 1);
    if (!r) r = holeExpr(b, l + 1);
    if (!r) r = litIntExpr(b, l + 1);
    if (!r) r = litStringExpr(b, l + 1);
    if (!r) r = univExpr(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // KW_LOOSER qualifiedId+
  public static boolean loosers(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "loosers")) return false;
    if (!nextTokenIs(b, KW_LOOSER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_LOOSER);
    r = r && loosers_1(b, l + 1);
    exit_section_(b, m, LOOSERS, r);
    return r;
  }

  // qualifiedId+
  private static boolean loosers_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "loosers_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = qualifiedId(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!qualifiedId(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "loosers_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // KW_MODULE weakId LBRACE stmt* RBRACE
  public static boolean module(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "module")) return false;
    if (!nextTokenIs(b, KW_MODULE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_MODULE);
    r = r && weakId(b, l + 1);
    r = r && consumeToken(b, LBRACE);
    r = r && module_3(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, MODULE, r);
    return r;
  }

  // stmt*
  private static boolean module_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "module_3")) return false;
    while (true) {
      int c = current_position_(b);
      if (!stmt(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "module_3", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // LBRACE weakId DEFINE_AS expr RBRACE
  public static boolean namedImArgument(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "namedImArgument")) return false;
    if (!nextTokenIs(b, LBRACE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACE);
    r = r && weakId(b, l + 1);
    r = r && consumeToken(b, DEFINE_AS);
    r = r && expr(b, l + 1, -1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, NAMED_IM_ARGUMENT, r);
    return r;
  }

  /* ********************************************************** */
  // BAR newArgField teleParamName* IMPLIES expr
  public static boolean newArg(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "newArg")) return false;
    if (!nextTokenIs(b, BAR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, BAR);
    r = r && newArgField(b, l + 1);
    r = r && newArg_2(b, l + 1);
    r = r && consumeToken(b, IMPLIES);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, NEW_ARG, r);
    return r;
  }

  // teleParamName*
  private static boolean newArg_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "newArg_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!teleParamName(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "newArg_2", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // weakId
  public static boolean newArgField(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "newArgField")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = weakId(b, l + 1);
    exit_section_(b, m, NEW_ARG_FIELD, r);
    return r;
  }

  /* ********************************************************** */
  // LBRACE newArg* RBRACE
  public static boolean newBody(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "newBody")) return false;
    if (!nextTokenIs(b, LBRACE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACE);
    r = r && newBody_1(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, NEW_BODY, r);
    return r;
  }

  // newArg*
  private static boolean newBody_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "newBody_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!newArg(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "newBody_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // KW_PUBLIC? KW_OPEN KW_IMPORT? qualifiedId useHide?
  public static boolean openCmd(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "openCmd")) return false;
    if (!nextTokenIs(b, "<open cmd>", KW_OPEN, KW_PUBLIC)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, OPEN_CMD, "<open cmd>");
    r = openCmd_0(b, l + 1);
    r = r && consumeToken(b, KW_OPEN);
    r = r && openCmd_2(b, l + 1);
    r = r && qualifiedId(b, l + 1);
    r = r && openCmd_4(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // KW_PUBLIC?
  private static boolean openCmd_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "openCmd_0")) return false;
    consumeToken(b, KW_PUBLIC);
    return true;
  }

  // KW_IMPORT?
  private static boolean openCmd_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "openCmd_2")) return false;
    consumeToken(b, KW_IMPORT);
    return true;
  }

  // useHide?
  private static boolean openCmd_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "openCmd_4")) return false;
    useHide(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // KW_OPEN
  public static boolean openKw(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "openKw")) return false;
    if (!nextTokenIs(b, KW_OPEN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_OPEN);
    exit_section_(b, m, OPEN_KW, r);
    return r;
  }

  /* ********************************************************** */
  // LBRACE partialInner RBRACE
  public static boolean partialBlock(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "partialBlock")) return false;
    if (!nextTokenIs(b, LBRACE)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, PARTIAL_BLOCK, null);
    r = consumeToken(b, LBRACE);
    p = r; // pin = 1
    r = r && report_error_(b, partialInner(b, l + 1));
    r = p && consumeToken(b, RBRACE) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // bareSubSystem? barredSubSystem*
  static boolean partialInner(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "partialInner")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = partialInner_0(b, l + 1);
    r = r && partialInner_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // bareSubSystem?
  private static boolean partialInner_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "partialInner_0")) return false;
    bareSubSystem(b, l + 1);
    return true;
  }

  // barredSubSystem*
  private static boolean partialInner_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "partialInner_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!barredSubSystem(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "partialInner_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // teleParamName
  public static boolean pathTele(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pathTele")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = teleParamName(b, l + 1);
    exit_section_(b, m, PATH_TELE, r);
    return r;
  }

  /* ********************************************************** */
  // unitPattern+ (KW_AS weakId)?
  public static boolean pattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PATTERN, "<pattern>");
    r = pattern_0(b, l + 1);
    r = r && pattern_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // unitPattern+
  private static boolean pattern_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = unitPattern(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!unitPattern(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "pattern_0", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // (KW_AS weakId)?
  private static boolean pattern_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_1")) return false;
    pattern_1_0(b, l + 1);
    return true;
  }

  // KW_AS weakId
  private static boolean pattern_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_AS);
    r = r && weakId(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<commaSep pattern>>
  public static boolean patterns(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "patterns")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PATTERNS, "<patterns>");
    r = commaSep(b, l + 1, AyaPsiParser::pattern);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // KW_PRIM primName tele* type?
  public static boolean primDecl(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "primDecl")) return false;
    if (!nextTokenIs(b, KW_PRIM)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_PRIM);
    r = r && primName(b, l + 1);
    r = r && primDecl_2(b, l + 1);
    r = r && primDecl_3(b, l + 1);
    exit_section_(b, m, PRIM_DECL, r);
    return r;
  }

  // tele*
  private static boolean primDecl_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "primDecl_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!tele(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "primDecl_2", c)) break;
    }
    return true;
  }

  // type?
  private static boolean primDecl_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "primDecl_3")) return false;
    type(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // weakId
  public static boolean primName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "primName")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = weakId(b, l + 1);
    exit_section_(b, m, PRIM_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // repl | stmts
  static boolean program(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "program")) return false;
    boolean r;
    r = repl(b, l + 1);
    if (!r) r = stmts(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // DOT NUMBER | DOT projFixId (expr (KW_FREEZE expr)?)?
  public static boolean projFix(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "projFix")) return false;
    if (!nextTokenIs(b, DOT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = parseTokens(b, 0, DOT, NUMBER);
    if (!r) r = projFix_1(b, l + 1);
    exit_section_(b, m, PROJ_FIX, r);
    return r;
  }

  // DOT projFixId (expr (KW_FREEZE expr)?)?
  private static boolean projFix_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "projFix_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOT);
    r = r && projFixId(b, l + 1);
    r = r && projFix_1_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (expr (KW_FREEZE expr)?)?
  private static boolean projFix_1_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "projFix_1_2")) return false;
    projFix_1_2_0(b, l + 1);
    return true;
  }

  // expr (KW_FREEZE expr)?
  private static boolean projFix_1_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "projFix_1_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = expr(b, l + 1, -1);
    r = r && projFix_1_2_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (KW_FREEZE expr)?
  private static boolean projFix_1_2_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "projFix_1_2_0_1")) return false;
    projFix_1_2_0_1_0(b, l + 1);
    return true;
  }

  // KW_FREEZE expr
  private static boolean projFix_1_2_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "projFix_1_2_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_FREEZE);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // qualifiedId
  public static boolean projFixId(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "projFixId")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = qualifiedId(b, l + 1);
    exit_section_(b, m, PROJ_FIX_ID, r);
    return r;
  }

  /* ********************************************************** */
  // weakId (COLON2 weakId)*
  public static boolean qualifiedId(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualifiedId")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = weakId(b, l + 1);
    r = r && qualifiedId_1(b, l + 1);
    exit_section_(b, m, QUALIFIED_ID, r);
    return r;
  }

  // (COLON2 weakId)*
  private static boolean qualifiedId_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualifiedId_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!qualifiedId_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "qualifiedId_1", c)) break;
    }
    return true;
  }

  // COLON2 weakId
  private static boolean qualifiedId_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualifiedId_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COLON2);
    r = r && weakId(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // qualifiedId
  public static boolean refExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "refExpr")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = qualifiedId(b, l + 1);
    exit_section_(b, m, REF_EXPR, r);
    return r;
  }

  /* ********************************************************** */
  // DOC_COMMENT+
  public static boolean remark(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "remark")) return false;
    if (!nextTokenIs(b, DOC_COMMENT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOC_COMMENT);
    while (r) {
      int c = current_position_(b);
      if (!consumeToken(b, DOC_COMMENT)) break;
      if (!empty_element_parsed_guard_(b, "remark", c)) break;
    }
    exit_section_(b, m, REMARK, r);
    return r;
  }

  /* ********************************************************** */
  // REPL_COMMAND? expr
  static boolean repl(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "repl")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = repl_0(b, l + 1);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  // REPL_COMMAND?
  private static boolean repl_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "repl_0")) return false;
    consumeToken(b, REPL_COMMAND);
    return true;
  }

  /* ********************************************************** */
  // KW_EXAMPLE | KW_COUNTEREXAMPLE
  public static boolean sampleModifiers(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "sampleModifiers")) return false;
    if (!nextTokenIs(b, "<sample modifiers>", KW_COUNTEREXAMPLE, KW_EXAMPLE)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, SAMPLE_MODIFIERS, "<sample modifiers>");
    r = consumeToken(b, KW_EXAMPLE);
    if (!r) r = consumeToken(b, KW_COUNTEREXAMPLE);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // decl
  //        | importCmd
  //        | openCmd
  //        | module
  //        | remark
  //        | generalize
  public static boolean stmt(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "stmt")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, STMT, "<stmt>");
    r = decl(b, l + 1);
    if (!r) r = importCmd(b, l + 1);
    if (!r) r = openCmd(b, l + 1);
    if (!r) r = module(b, l + 1);
    if (!r) r = remark(b, l + 1);
    if (!r) r = generalize(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // KW_PUBLIC | KW_PRIVATE | KW_OPEN | KW_IMPORT | KW_MODULE
  //                      | KW_EXAMPLE | KW_COUNTEREXAMPLE | DOC_COMMENT
  //                      | KW_DEF | KW_STRUCT | KW_PRIM | KW_DATA | KW_VARIABLE
  static boolean stmt_first(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "stmt_first")) return false;
    boolean r;
    r = consumeToken(b, KW_PUBLIC);
    if (!r) r = consumeToken(b, KW_PRIVATE);
    if (!r) r = consumeToken(b, KW_OPEN);
    if (!r) r = consumeToken(b, KW_IMPORT);
    if (!r) r = consumeToken(b, KW_MODULE);
    if (!r) r = consumeToken(b, KW_EXAMPLE);
    if (!r) r = consumeToken(b, KW_COUNTEREXAMPLE);
    if (!r) r = consumeToken(b, DOC_COMMENT);
    if (!r) r = consumeToken(b, KW_DEF);
    if (!r) r = consumeToken(b, KW_STRUCT);
    if (!r) r = consumeToken(b, KW_PRIM);
    if (!r) r = consumeToken(b, KW_DATA);
    if (!r) r = consumeToken(b, KW_VARIABLE);
    return r;
  }

  /* ********************************************************** */
  // !(stmt_first)
  static boolean stmt_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "stmt_recover")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !stmt_recover_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (stmt_first)
  private static boolean stmt_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "stmt_recover_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = stmt_first(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // !(<<eof>>) stmt
  static boolean stmt_with_recover(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "stmt_with_recover")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = stmt_with_recover_0(b, l + 1);
    p = r; // pin = 1
    r = r && stmt(b, l + 1);
    exit_section_(b, l, m, r, p, AyaPsiParser::stmt_recover);
    return r || p;
  }

  // !(<<eof>>)
  private static boolean stmt_with_recover_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "stmt_with_recover_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !stmt_with_recover_0_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // <<eof>>
  private static boolean stmt_with_recover_0_0(PsiBuilder b, int l) {
    return eof(b, l + 1);
  }

  /* ********************************************************** */
  // stmt_with_recover*
  static boolean stmts(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "stmts")) return false;
    while (true) {
      int c = current_position_(b);
      if (!stmt_with_recover(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "stmts", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // sampleModifiers? (KW_PUBLIC? openKw)?
  //        KW_STRUCT declNameOrInfix tele* type? (KW_EXTENDS idsComma)?
  //        (BAR structField)* bindBlock?
  public static boolean structDecl(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structDecl")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STRUCT_DECL, "<struct decl>");
    r = structDecl_0(b, l + 1);
    r = r && structDecl_1(b, l + 1);
    r = r && consumeToken(b, KW_STRUCT);
    r = r && declNameOrInfix(b, l + 1);
    r = r && structDecl_4(b, l + 1);
    r = r && structDecl_5(b, l + 1);
    r = r && structDecl_6(b, l + 1);
    r = r && structDecl_7(b, l + 1);
    r = r && structDecl_8(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // sampleModifiers?
  private static boolean structDecl_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structDecl_0")) return false;
    sampleModifiers(b, l + 1);
    return true;
  }

  // (KW_PUBLIC? openKw)?
  private static boolean structDecl_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structDecl_1")) return false;
    structDecl_1_0(b, l + 1);
    return true;
  }

  // KW_PUBLIC? openKw
  private static boolean structDecl_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structDecl_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = structDecl_1_0_0(b, l + 1);
    r = r && openKw(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // KW_PUBLIC?
  private static boolean structDecl_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structDecl_1_0_0")) return false;
    consumeToken(b, KW_PUBLIC);
    return true;
  }

  // tele*
  private static boolean structDecl_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structDecl_4")) return false;
    while (true) {
      int c = current_position_(b);
      if (!tele(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "structDecl_4", c)) break;
    }
    return true;
  }

  // type?
  private static boolean structDecl_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structDecl_5")) return false;
    type(b, l + 1);
    return true;
  }

  // (KW_EXTENDS idsComma)?
  private static boolean structDecl_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structDecl_6")) return false;
    structDecl_6_0(b, l + 1);
    return true;
  }

  // KW_EXTENDS idsComma
  private static boolean structDecl_6_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structDecl_6_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_EXTENDS);
    r = r && idsComma(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (BAR structField)*
  private static boolean structDecl_7(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structDecl_7")) return false;
    while (true) {
      int c = current_position_(b);
      if (!structDecl_7_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "structDecl_7", c)) break;
    }
    return true;
  }

  // BAR structField
  private static boolean structDecl_7_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structDecl_7_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, BAR);
    r = r && structField(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // bindBlock?
  private static boolean structDecl_8(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structDecl_8")) return false;
    bindBlock(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // KW_OVERRIDE?            declNameOrInfix tele* type? IMPLIES expr bindBlock?
  //     | KW_OVERRIDE? KW_COERCE? declNameOrInfix tele* type               bindBlock?
  public static boolean structField(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structField")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STRUCT_FIELD, "<struct field>");
    r = structField_0(b, l + 1);
    if (!r) r = structField_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // KW_OVERRIDE?            declNameOrInfix tele* type? IMPLIES expr bindBlock?
  private static boolean structField_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structField_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = structField_0_0(b, l + 1);
    r = r && declNameOrInfix(b, l + 1);
    r = r && structField_0_2(b, l + 1);
    r = r && structField_0_3(b, l + 1);
    r = r && consumeToken(b, IMPLIES);
    r = r && expr(b, l + 1, -1);
    r = r && structField_0_6(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // KW_OVERRIDE?
  private static boolean structField_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structField_0_0")) return false;
    consumeToken(b, KW_OVERRIDE);
    return true;
  }

  // tele*
  private static boolean structField_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structField_0_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!tele(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "structField_0_2", c)) break;
    }
    return true;
  }

  // type?
  private static boolean structField_0_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structField_0_3")) return false;
    type(b, l + 1);
    return true;
  }

  // bindBlock?
  private static boolean structField_0_6(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structField_0_6")) return false;
    bindBlock(b, l + 1);
    return true;
  }

  // KW_OVERRIDE? KW_COERCE? declNameOrInfix tele* type               bindBlock?
  private static boolean structField_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structField_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = structField_1_0(b, l + 1);
    r = r && structField_1_1(b, l + 1);
    r = r && declNameOrInfix(b, l + 1);
    r = r && structField_1_3(b, l + 1);
    r = r && type(b, l + 1);
    r = r && structField_1_5(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // KW_OVERRIDE?
  private static boolean structField_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structField_1_0")) return false;
    consumeToken(b, KW_OVERRIDE);
    return true;
  }

  // KW_COERCE?
  private static boolean structField_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structField_1_1")) return false;
    consumeToken(b, KW_COERCE);
    return true;
  }

  // tele*
  private static boolean structField_1_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structField_1_3")) return false;
    while (true) {
      int c = current_position_(b);
      if (!tele(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "structField_1_3", c)) break;
    }
    return true;
  }

  // bindBlock?
  private static boolean structField_1_5(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "structField_1_5")) return false;
    bindBlock(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // expr DEFINE_AS expr
  public static boolean subSystem(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "subSystem")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, SUB_SYSTEM, "<sub system>");
    r = expr(b, l + 1, -1);
    r = r && consumeToken(b, DEFINE_AS);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // teleLit
  //        | teleEx
  //        | teleIm
  public static boolean tele(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tele")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TELE, "<tele>");
    r = teleLit(b, l + 1);
    if (!r) r = teleEx(b, l + 1);
    if (!r) r = teleIm(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // teleBinderTyped
  //              | teleBinderAnonymous
  public static boolean teleBinder(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "teleBinder")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TELE_BINDER, "<tele binder>");
    r = teleBinderTyped(b, l + 1);
    if (!r) r = teleBinderAnonymous(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // expr
  public static boolean teleBinderAnonymous(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "teleBinderAnonymous")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TELE_BINDER_ANONYMOUS, "<tele binder anonymous>");
    r = expr(b, l + 1, -1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // teleParamName+ type
  public static boolean teleBinderTyped(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "teleBinderTyped")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = teleBinderTyped_0(b, l + 1);
    r = r && type(b, l + 1);
    exit_section_(b, m, TELE_BINDER_TYPED, r);
    return r;
  }

  // teleParamName+
  private static boolean teleBinderTyped_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "teleBinderTyped_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = teleParamName(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!teleParamName(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "teleBinderTyped_0", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // LPAREN teleBinder RPAREN
  public static boolean teleEx(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "teleEx")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && teleBinder(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, TELE_EX, r);
    return r;
  }

  /* ********************************************************** */
  // LBRACE teleBinder RBRACE
  public static boolean teleIm(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "teleIm")) return false;
    if (!nextTokenIs(b, LBRACE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACE);
    r = r && teleBinder(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, TELE_IM, r);
    return r;
  }

  /* ********************************************************** */
  // literal
  public static boolean teleLit(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "teleLit")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, TELE_LIT, "<tele lit>");
    r = literal(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // weakId
  public static boolean teleParamName(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "teleParamName")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = weakId(b, l + 1);
    exit_section_(b, m, TELE_PARAM_NAME, r);
    return r;
  }

  /* ********************************************************** */
  // KW_TIGHTER qualifiedId+
  public static boolean tighters(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tighters")) return false;
    if (!nextTokenIs(b, KW_TIGHTER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_TIGHTER);
    r = r && tighters_1(b, l + 1);
    exit_section_(b, m, TIGHTERS, r);
    return r;
  }

  // qualifiedId+
  private static boolean tighters_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tighters_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = qualifiedId(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!qualifiedId(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "tighters_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // LPAREN exprList RPAREN
  public static boolean tupleAtom(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tupleAtom")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && exprList(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, TUPLE_ATOM, r);
    return r;
  }

  /* ********************************************************** */
  // LBRACE exprList RBRACE
  public static boolean tupleImArgument(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "tupleImArgument")) return false;
    if (!nextTokenIs(b, LBRACE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACE);
    r = r && exprList(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, TUPLE_IM_ARGUMENT, r);
    return r;
  }

  /* ********************************************************** */
  // COLON expr
  public static boolean type(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "type")) return false;
    if (!nextTokenIs(b, COLON)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, TYPE, null);
    r = consumeToken(b, COLON);
    p = r; // pin = 1
    r = r && expr(b, l + 1, -1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // uliftPrefix* literal
  public static boolean uliftAtom(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uliftAtom")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, ULIFT_ATOM, "<ulift atom>");
    r = uliftAtom_0(b, l + 1);
    r = r && literal(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // uliftPrefix*
  private static boolean uliftAtom_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uliftAtom_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!uliftPrefix(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "uliftAtom_0", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // KW_ULIFT
  public static boolean uliftPrefix(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "uliftPrefix")) return false;
    if (!nextTokenIs(b, KW_ULIFT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_ULIFT);
    exit_section_(b, m, ULIFT_PREFIX, r);
    return r;
  }

  /* ********************************************************** */
  // LPAREN patterns RPAREN
  //               | LBRACE patterns RBRACE
  //               | atomPattern
  public static boolean unitPattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unitPattern")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, UNIT_PATTERN, "<unit pattern>");
    r = unitPattern_0(b, l + 1);
    if (!r) r = unitPattern_1(b, l + 1);
    if (!r) r = atomPattern(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // LPAREN patterns RPAREN
  private static boolean unitPattern_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unitPattern_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && patterns(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, null, r);
    return r;
  }

  // LBRACE patterns RBRACE
  private static boolean unitPattern_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "unitPattern_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LBRACE);
    r = r && patterns(b, l + 1);
    r = r && consumeToken(b, RBRACE);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // KW_TYPE | KW_SET | KW_PROP | KW_ISET
  public static boolean univExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "univExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, UNIV_EXPR, "<univ expr>");
    r = consumeToken(b, KW_TYPE);
    if (!r) r = consumeToken(b, KW_SET);
    if (!r) r = consumeToken(b, KW_PROP);
    if (!r) r = consumeToken(b, KW_ISET);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // KW_AS assoc? weakId bindBlock?
  public static boolean useAs(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "useAs")) return false;
    if (!nextTokenIs(b, KW_AS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_AS);
    r = r && useAs_1(b, l + 1);
    r = r && weakId(b, l + 1);
    r = r && useAs_3(b, l + 1);
    exit_section_(b, m, USE_AS, r);
    return r;
  }

  // assoc?
  private static boolean useAs_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "useAs_1")) return false;
    assoc(b, l + 1);
    return true;
  }

  // bindBlock?
  private static boolean useAs_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "useAs_3")) return false;
    bindBlock(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // KW_USING useList+ | KW_HIDING hideList+
  public static boolean useHide(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "useHide")) return false;
    if (!nextTokenIs(b, "<use hide>", KW_HIDING, KW_USING)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, USE_HIDE, "<use hide>");
    r = useHide_0(b, l + 1);
    if (!r) r = useHide_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // KW_USING useList+
  private static boolean useHide_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "useHide_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_USING);
    r = r && useHide_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // useList+
  private static boolean useHide_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "useHide_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = useList(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!useList(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "useHide_0_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // KW_HIDING hideList+
  private static boolean useHide_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "useHide_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KW_HIDING);
    r = r && useHide_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // hideList+
  private static boolean useHide_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "useHide_1_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = hideList(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!hideList(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "useHide_1_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // weakId useAs?
  public static boolean useId(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "useId")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = weakId(b, l + 1);
    r = r && useId_1(b, l + 1);
    exit_section_(b, m, USE_ID, r);
    return r;
  }

  // useAs?
  private static boolean useId_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "useId_1")) return false;
    useAs(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // <<commaSep useId>>?
  public static boolean useIdsComma(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "useIdsComma")) return false;
    Marker m = enter_section_(b, l, _NONE_, USE_IDS_COMMA, "<use ids comma>");
    commaSep(b, l + 1, AyaPsiParser::useId);
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // LPAREN useIdsComma RPAREN
  public static boolean useList(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "useList")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LPAREN);
    r = r && useIdsComma(b, l + 1);
    r = r && consumeToken(b, RPAREN);
    exit_section_(b, m, USE_LIST, r);
    return r;
  }

  /* ********************************************************** */
  // ID
  public static boolean weakId(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "weakId")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ID);
    exit_section_(b, m, WEAK_ID, r);
    return r;
  }

  /* ********************************************************** */
  // Expression root: expr
  // Operator priority table:
  // 0: PREFIX(newExpr)
  // 1: PREFIX(piExpr)
  // 2: PREFIX(forallExpr)
  // 3: PREFIX(sigmaExpr)
  // 4: ATOM(lambdaExpr)
  // 5: ATOM(matchExpr)
  // 6: PREFIX(letExpr)
  // 7: ATOM(doExpr)
  // 8: ATOM(thisExpr)
  // 9: ATOM(partialExpr)
  // 10: ATOM(pathExpr)
  // 11: ATOM(atomExpr)
  // 12: BINARY(arrowExpr)
  // 13: POSTFIX(appExpr)
  // 14: POSTFIX(projExpr)
  public static boolean expr(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expr")) return false;
    addVariant(b, "<expr>");
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, "<expr>");
    r = newExpr(b, l + 1);
    if (!r) r = piExpr(b, l + 1);
    if (!r) r = forallExpr(b, l + 1);
    if (!r) r = sigmaExpr(b, l + 1);
    if (!r) r = lambdaExpr(b, l + 1);
    if (!r) r = matchExpr(b, l + 1);
    if (!r) r = letExpr(b, l + 1);
    if (!r) r = doExpr(b, l + 1);
    if (!r) r = thisExpr(b, l + 1);
    if (!r) r = partialExpr(b, l + 1);
    if (!r) r = pathExpr(b, l + 1);
    if (!r) r = atomExpr(b, l + 1);
    p = r;
    r = r && expr_0(b, l + 1, g);
    exit_section_(b, l, m, null, r, p, null);
    return r || p;
  }

  public static boolean expr_0(PsiBuilder b, int l, int g) {
    if (!recursion_guard_(b, l, "expr_0")) return false;
    boolean r = true;
    while (true) {
      Marker m = enter_section_(b, l, _LEFT_, null);
      if (g < 12 && consumeTokenSmart(b, TO)) {
        r = expr(b, l, 11);
        exit_section_(b, l, m, ARROW_EXPR, r, true, null);
      }
      else if (g < 13 && appExpr_0(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, APP_EXPR, r, true, null);
      }
      else if (g < 14 && projFix(b, l + 1)) {
        r = true;
        exit_section_(b, l, m, PROJ_EXPR, r, true, null);
      }
      else {
        exit_section_(b, l, m, null, false, false, null);
        break;
      }
    }
    return r;
  }

  public static boolean newExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "newExpr")) return false;
    if (!nextTokenIsSmart(b, KW_NEW)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = consumeTokenSmart(b, KW_NEW);
    p = r;
    r = p && expr(b, l, 0);
    r = p && report_error_(b, newExpr_1(b, l + 1)) && r;
    exit_section_(b, l, m, NEW_EXPR, r, p, null);
    return r || p;
  }

  // newBody?
  private static boolean newExpr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "newExpr_1")) return false;
    newBody(b, l + 1);
    return true;
  }

  public static boolean piExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "piExpr")) return false;
    if (!nextTokenIsSmart(b, KW_PI)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = piExpr_0(b, l + 1);
    p = r;
    r = p && expr(b, l, 1);
    exit_section_(b, l, m, PI_EXPR, r, p, null);
    return r || p;
  }

  // KW_PI tele+ TO
  private static boolean piExpr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "piExpr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, KW_PI);
    r = r && piExpr_0_1(b, l + 1);
    r = r && consumeToken(b, TO);
    exit_section_(b, m, null, r);
    return r;
  }

  // tele+
  private static boolean piExpr_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "piExpr_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = tele(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!tele(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "piExpr_0_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  public static boolean forallExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "forallExpr")) return false;
    if (!nextTokenIsSmart(b, KW_FORALL)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = forallExpr_0(b, l + 1);
    p = r;
    r = p && expr(b, l, 2);
    exit_section_(b, l, m, FORALL_EXPR, r, p, null);
    return r || p;
  }

  // KW_FORALL lambdaTele+ TO
  private static boolean forallExpr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "forallExpr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, KW_FORALL);
    r = r && forallExpr_0_1(b, l + 1);
    r = r && consumeToken(b, TO);
    exit_section_(b, m, null, r);
    return r;
  }

  // lambdaTele+
  private static boolean forallExpr_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "forallExpr_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = lambdaTele(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!lambdaTele(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "forallExpr_0_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  public static boolean sigmaExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "sigmaExpr")) return false;
    if (!nextTokenIsSmart(b, KW_SIGMA)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = sigmaExpr_0(b, l + 1);
    p = r;
    r = p && expr(b, l, 3);
    exit_section_(b, l, m, SIGMA_EXPR, r, p, null);
    return r || p;
  }

  // KW_SIGMA tele+ SUCHTHAT
  private static boolean sigmaExpr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "sigmaExpr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, KW_SIGMA);
    r = r && sigmaExpr_0_1(b, l + 1);
    r = r && consumeToken(b, SUCHTHAT);
    exit_section_(b, m, null, r);
    return r;
  }

  // tele+
  private static boolean sigmaExpr_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "sigmaExpr_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = tele(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!tele(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "sigmaExpr_0_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // KW_LAMBDA lambdaTele+ (IMPLIES expr)?
  public static boolean lambdaExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lambdaExpr")) return false;
    if (!nextTokenIsSmart(b, KW_LAMBDA)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, KW_LAMBDA);
    r = r && lambdaExpr_1(b, l + 1);
    r = r && lambdaExpr_2(b, l + 1);
    exit_section_(b, m, LAMBDA_EXPR, r);
    return r;
  }

  // lambdaTele+
  private static boolean lambdaExpr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lambdaExpr_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = lambdaTele(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!lambdaTele(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "lambdaExpr_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // (IMPLIES expr)?
  private static boolean lambdaExpr_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lambdaExpr_2")) return false;
    lambdaExpr_2_0(b, l + 1);
    return true;
  }

  // IMPLIES expr
  private static boolean lambdaExpr_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "lambdaExpr_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, IMPLIES);
    r = r && expr(b, l + 1, -1);
    exit_section_(b, m, null, r);
    return r;
  }

  // KW_MATCH exprList clauses
  public static boolean matchExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "matchExpr")) return false;
    if (!nextTokenIsSmart(b, KW_MATCH)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, KW_MATCH);
    r = r && exprList(b, l + 1);
    r = r && clauses(b, l + 1);
    exit_section_(b, m, MATCH_EXPR, r);
    return r;
  }

  public static boolean letExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "letExpr")) return false;
    if (!nextTokenIsSmart(b, KW_LET)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, null);
    r = letExpr_0(b, l + 1);
    p = r;
    r = p && expr(b, l, 6);
    exit_section_(b, l, m, LET_EXPR, r, p, null);
    return r || p;
  }

  // KW_LET <<commaSep letBind>> KW_IN
  private static boolean letExpr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "letExpr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, KW_LET);
    r = r && commaSep(b, l + 1, AyaPsiParser::letBind);
    r = r && consumeToken(b, KW_IN);
    exit_section_(b, m, null, r);
    return r;
  }

  // KW_DO LBRACE? doBlock RBRACE?
  public static boolean doExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "doExpr")) return false;
    if (!nextTokenIsSmart(b, KW_DO)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, KW_DO);
    r = r && doExpr_1(b, l + 1);
    r = r && doBlock(b, l + 1);
    r = r && doExpr_3(b, l + 1);
    exit_section_(b, m, DO_EXPR, r);
    return r;
  }

  // LBRACE?
  private static boolean doExpr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "doExpr_1")) return false;
    consumeTokenSmart(b, LBRACE);
    return true;
  }

  // RBRACE?
  private static boolean doExpr_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "doExpr_3")) return false;
    consumeTokenSmart(b, RBRACE);
    return true;
  }

  // KW_THIS (AT qualifiedId)?
  public static boolean thisExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "thisExpr")) return false;
    if (!nextTokenIsSmart(b, KW_THIS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, KW_THIS);
    r = r && thisExpr_1(b, l + 1);
    exit_section_(b, m, THIS_EXPR, r);
    return r;
  }

  // (AT qualifiedId)?
  private static boolean thisExpr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "thisExpr_1")) return false;
    thisExpr_1_0(b, l + 1);
    return true;
  }

  // AT qualifiedId
  private static boolean thisExpr_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "thisExpr_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, AT);
    r = r && qualifiedId(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // LPARTIAL partialInner? RPARTIAL
  public static boolean partialExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "partialExpr")) return false;
    if (!nextTokenIsSmart(b, LPARTIAL)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, LPARTIAL);
    r = r && partialExpr_1(b, l + 1);
    r = r && consumeToken(b, RPARTIAL);
    exit_section_(b, m, PARTIAL_EXPR, r);
    return r;
  }

  // partialInner?
  private static boolean partialExpr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "partialExpr_1")) return false;
    partialInner(b, l + 1);
    return true;
  }

  // LPATH pathTele+ RPATH expr partialExpr?
  public static boolean pathExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pathExpr")) return false;
    if (!nextTokenIsSmart(b, LPATH)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokenSmart(b, LPATH);
    r = r && pathExpr_1(b, l + 1);
    r = r && consumeToken(b, RPATH);
    r = r && expr(b, l + 1, -1);
    r = r && pathExpr_4(b, l + 1);
    exit_section_(b, m, PATH_EXPR, r);
    return r;
  }

  // pathTele+
  private static boolean pathExpr_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pathExpr_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pathTele(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!pathTele(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "pathExpr_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // partialExpr?
  private static boolean pathExpr_4(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pathExpr_4")) return false;
    partialExpr(b, l + 1);
    return true;
  }

  // uliftAtom
  //            | tupleAtom
  //            | idiomAtom
  //            | arrayAtom
  public static boolean atomExpr(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "atomExpr")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, ATOM_EXPR, "<atom expr>");
    r = uliftAtom(b, l + 1);
    if (!r) r = tupleAtom(b, l + 1);
    if (!r) r = idiomAtom(b, l + 1);
    if (!r) r = arrayAtom(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // argument+
  private static boolean appExpr_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "appExpr_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = argument(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!argument(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "appExpr_0", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  static final Parser expr_parser_ = (b, l) -> expr(b, l + 1, -1);
}
