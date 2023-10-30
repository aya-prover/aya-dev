// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

// This is a generated file. Not intended for manual editing.
package org.aya.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static org.aya.parser.FlclPsiElementTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class FlclPsiParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, null);
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

  /* ********************************************************** */
  // token*
  public static boolean body(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "body")) return false;
    Marker m = enter_section_(b, l, _NONE_, BODY, "<body>");
    while (true) {
      int c = current_position_(b);
      if (!token(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "body", c)) break;
    }
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // rule* SEPARATOR body
  static boolean program(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "program")) return false;
    if (!nextTokenIs(b, "", ID, SEPARATOR)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = program_0(b, l + 1);
    r = r && consumeToken(b, SEPARATOR);
    r = r && body(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // rule*
  private static boolean program_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "program_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!rule(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "program_0", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // ID COLON ID* SEMI
  public static boolean rule(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "rule")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, ID, COLON);
    r = r && rule_2(b, l + 1);
    r = r && consumeToken(b, SEMI);
    exit_section_(b, m, RULE, r);
    return r;
  }

  // ID*
  private static boolean rule_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "rule_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!consumeToken(b, ID)) break;
      if (!empty_element_parsed_guard_(b, "rule_2", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // ID | NUMBER
  static boolean token(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "token")) return false;
    if (!nextTokenIs(b, "", ID, NUMBER)) return false;
    boolean r;
    r = consumeToken(b, ID);
    if (!r) r = consumeToken(b, NUMBER);
    return r;
  }

}
