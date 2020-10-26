// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.generic;

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
  FixR(false);

  public final boolean infix;

  Assoc(boolean infix) {
    this.infix = infix;
  }
}
