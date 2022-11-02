// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CodifierTest extends ReplTestBase {
  @Test public void basic() {
    var out = repl("""
      prim I
      def pmap {A B : Type} (p : [| i |] A) (f : A -> B) : [| i |] B => \\i => f (p i)
      :codify pmap
      """)._1.trim();
    // It would be nice if we can generate a complete Java file
    //  and run the code to test the codifier
    assertNotNull(out);
  }
}
