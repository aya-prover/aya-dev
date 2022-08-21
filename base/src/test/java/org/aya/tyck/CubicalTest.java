// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import org.junit.jupiter.api.Test;

public class CubicalTest {
  @Test public void partial() {
    TyckDeclTest.successTyckDecls("""
      def t (A : Type) (i : I) (a : A) : Partial A { i 0 }
        => {| i 0 := a |}
          
      def t2 (A : Type) (a : A) (i : I) : Partial A { i 0 }
        => {| i 0 := a | i 1 := a |}
          
      def t3 (A : Type) (i : I) (a : A) (b : A) : Partial A { i 0 \\/ i 1 } =>
        {| i 0 := a | i 1 := b |}
          
      def t4 (A : Type) (i : I) (j : I) (a : A) (b : A) : Partial A { i 0 \\/ i 1 /\\ j 0 } =>
        {| i 0 := a | i 1 /\\ j 0 := b |}
          
      def t5 (A : Type) (i : I) (j : I) (a : A) (b : A) : Partial A { i 0 \\/ i 1 /\\ j 0 } =>
        {| i 0 := a | i 1 := b |}
      """);
  }
}
