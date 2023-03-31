// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty;

import org.aya.pretty.doc.Doc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Reproduction {
  @Test public void mock() {
    var doc = Doc.nest(2, Doc.code("hey"));
    assertEquals("  `hey'", doc.renderToTerminal());
    assertEquals("\\hspace{1.0em}\\texttt{hey}", doc.renderToTeX());
  }
}
