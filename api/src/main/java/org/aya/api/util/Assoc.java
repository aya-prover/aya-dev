// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.util;

/**
 * @author ice1000
 */
public enum Assoc {
  /**
   * Parenthesis operator fixity.
   */
  Twin(false),
  Infix(true),
  InfixL(true),
  InfixR(true),
  Fix(false),
  FixL(false),
  FixR(false),
  NoFix(false);

  /**
   * That this fixity is infix.
   */
  public final boolean infix;

  Assoc(boolean infix) {
    this.infix = infix;
  }
}
