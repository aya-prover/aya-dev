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
      def coePi (r s : I) (A : I -> Type) (B : ∀ (i : I) -> A i -> Type) (f : ∀ (a : A r) -> B r a) : ∀ (a : A s) -> B s a => \\a => coe r s (\\x => B x (coe s x A a)) (f (coe s r A a))
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
