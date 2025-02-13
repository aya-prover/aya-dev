// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.prettier;

import org.aya.pretty.doc.Doc;

import static org.aya.prettier.BasePrettier.*;

public final class Tokens {

  private Tokens() {
  }

  public static final Doc LAMBDA = Doc.styled(KEYWORD, Doc.symbol("fn"));
  public static final Doc ARROW = Doc.symbol("->");
  public static final Doc LARROW = Doc.symbol("<-");
  public static final Doc FN_DEFINED_AS = Doc.symbol("=>");
  public static final Doc DEFINED_AS = Doc.symbol(":=");
  public static final Doc HOLE = Doc.symbol("{??}");
  public static final Doc HOLE_LEFT = Doc.symbol("{?");
  public static final Doc HOLE_RIGHT = Doc.symbol("?}");
  public static final Doc META_LEFT = Doc.symbol("<");
  public static final Doc META_RIGHT = Doc.symbol(">");
  public static final Doc BAR = Doc.symbol("|");
  public static final Doc HAS_TYPE = Doc.symbol(":");
  public static final Doc PROJ = Doc.symbol(".");
  public static final Doc SIGMA_RESULT = Doc.styled(KEYWORD, "**");
  public static final Doc LIST_LEFT = Doc.symbol("[");
  public static final Doc LIST_RIGHT = Doc.symbol("]");
  public static final Doc EQ = Doc.symbol("=");
  public static final Doc AT = Doc.symbol("@");

  public static final Doc KW_DO = Doc.styled(KEYWORD, "do");
  public static final Doc KW_AS = Doc.styled(KEYWORD, "as");
  public static final Doc KW_SIGMA = Doc.styled(KEYWORD, Doc.symbol("Sig"));
  public static final Doc KW_PI = Doc.styled(KEYWORD, "Fn");
  public static final Doc KW_LET = Doc.styled(KEYWORD, "let");
  public static final Doc KW_IN = Doc.styled(KEYWORD, "in");
  public static final Doc KW_MATCH = Doc.styled(KEYWORD, "match");
  public static final Doc KW_DEF = Doc.styled(KEYWORD, "def");
  public static final Doc KW_DATA = Doc.styled(KEYWORD, "inductive");
  public static final Doc KW_CLASS = Doc.styled(KEYWORD, "class");
  public static final Doc KW_NEW = Doc.styled(KEYWORD, "new");
  public static final Doc PAT_ABSURD = Doc.styled(KEYWORD, "()");
  public static final Doc KW_TIGHTER = Doc.styled(KEYWORD, "tighter");
  public static final Doc KW_LOOSER = Doc.styled(KEYWORD, "looser");
  public static final Doc KW_BIND = Doc.styled(KEYWORD, "bind");
  public static final Doc KW_ELIM = Doc.styled(KEYWORD, "elim");
  public static final Doc KW_PRIM = Doc.styled(KEYWORD, "prim");
  public static final Doc KW_VARIABLES = Doc.styled(KEYWORD, "variables");
  public static final Doc KW_IMPORT = Doc.styled(KEYWORD, "import");
  public static final Doc KW_INTERVAL = Doc.styled(PRIM, "I");
  public static final Doc KW_COE = Doc.styled(KEYWORD, "coe");
  public static final Doc KW_PARTIAL_TYPE = Doc.styled(KEYWORD, "Partial");
  public static final Doc KW_PARTIAL = Doc.styled(KEYWORD, "partial");

  public static final Doc COMMENT_COMPILED_CODE = Doc.styled(COMMENT, "/* compiled code */");
  public static final Doc COMMENT_COMPILED_PATTERN = Doc.styled(COMMENT, "/* compiled pattern */");
}
