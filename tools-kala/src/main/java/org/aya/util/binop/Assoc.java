// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.binop;

public enum Assoc {
  /** infix */
  Infix,
  /** infix, left-associative */
  InfixL,
  /** infix, right-associative */
  InfixR,
  /** prefix operators */
  FixL,
  /** postfix operators */
  FixR,

  Invalid;

  public boolean isBinary() {
    return this == Infix || this == InfixL || this == InfixR;
  }

  public boolean isUnary() {
    return this == FixL || this == FixR;
  }

  public boolean leftAssoc() {
    return this == InfixL || this == FixL;
  }

  public boolean noAssoc() {
    return this == Infix;
  }

  public static boolean assocAmbiguous(Assoc a, Assoc b) {
    //noinspection ConstantConditions: I know, I know. But we should also plan for the future.
    return a != b || a.noAssoc() || b.noAssoc();
  }
}
