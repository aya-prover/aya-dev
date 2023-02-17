// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CodifierTest extends ReplTestBase {
  @Test public void coePi() {
    var out = repl("""
      prim I prim coe prim intervalInv prim intervalMax
      inline def ~ => intervalInv
      inline def infix ∨ => intervalMax
      def coePi (A : I -> Type) (B : Pi (i : I) -> A i -> Type) (f : Pi (a : A 0) -> B 0 a) : Pi (a : A 1) -> B 1 a => \\a => (\\i => B i ((\\j => A ((~ j) ∨ i)).coe a freeze i)).coe f ((\\i => A (~ i)).coe a)
      :codify coePi
      """).component1().trim();
    assertNotNull(out);
  }

  @Test public void pmap() {
    var out = repl("""
      prim I
      def pmap {A B : Type} (p : [| i |] A) (f : A -> B) : [| i |] B => \\i => f (p i)
      :codify pmap
      """).component1().trim();
    // It would be nice if we can generate a complete Java file
    //  and run the code to test the codifier
    assertNotNull(out);
  }
}
