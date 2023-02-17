// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cubical;

import org.aya.prettier.AyaPrettierOptions;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.TyckDeclTest;
import org.junit.jupiter.api.Test;

import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PartialTest {
  @Test public void partial() {
    var res = TyckDeclTest.successTyckDecls("""
      prim I
      prim Partial
      prim intervalMin
      prim intervalMax
      prim intervalInv
            
      inline def infix /\\ => intervalMin
      tighter \\/

      inline def infix \\/ => intervalMax

      inline def ~ => intervalInv

      def t (A : Type) (i : I) (a : A) : Partial (~ i) A
        => {| ~ i := a |}

      def t2 (A : Type) (a : A) (i : I) : Partial (~ i) A
        => {| ~ i := a | i := a |}
          
      def t3 (A : Type) (i : I) (a : A) (b : A) : Partial (~ i \\/ i) A =>
        {| ~ i := a | i := b |}
          
      def t4 (A : Type) (i : I) (j : I) (a : A) (b : A) : Partial (~ i \\/ i /\\ ~ j) A =>
        {| ~ i := a | i /\\ ~ j := b |}
          
      def t5 (A : Type) (i : I) (j : I) (a : A) (b : A) : Partial (~ i \\/ i /\\ ~ j) A =>
        {| ~ i := a | i := b |}
      """);
    IntFunction<Doc> prettier = i -> res.component2().get(i).toDoc(AyaPrettierOptions.debug());
    assertEquals("""
      def t (A : Type 0) (i : I) (a : A) : Partial A (~ i) => {| ~ i := a |}
      """.strip(), prettier.apply(8).debugRender());
  }
}
