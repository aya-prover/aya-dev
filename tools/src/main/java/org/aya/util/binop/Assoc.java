// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.binop;

/**
 * @author ice1000
 */
public enum Assoc {
  /**
   * Parenthesis operator fixity.
   */
  Infix(true),
  InfixL(true),
  InfixR(true),
  Invalid(false);

  /**
   * That this fixity is infix.
   */
  public final boolean infix;

  Assoc(boolean infix) {
    this.infix = infix;
  }
}
