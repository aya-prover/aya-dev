// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.experiments;

import org.commonmark.parser.Parser;
import org.junit.jupiter.api.Test;

public class CommonMark {
  @Test public void backend() {
    var parser = Parser.builder().build();
    var node = parser.parse("hey `ast`");
    System.out.println(node);

  }
}
