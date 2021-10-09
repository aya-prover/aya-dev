// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.experiments;

import org.aya.concrete.remark.CodeOptions;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CommonMark {
  @Test public void backend() {
    var parser = Parser.builder().build();
    var node = parser.parse("hey `ast`");
    assertNotNull(node);
    // System.out.println(node);
  }

  @Test public void regexCodeOptions() {
    var matcher1 = CodeOptions.PARSER.matcher("CT: wow A B");
    var matcher2 = CodeOptions.PARSER.matcher("CT|I|K: anqur x y");
    assertTrue(matcher1.find());
    assertEquals("CT", matcher1.group(2));
    assertEquals(" wow A B", matcher1.group(6));
    assertTrue(matcher2.find());
    assertEquals("CT", matcher2.group(2));
    assertEquals("I", matcher2.group(4));
    assertEquals("K", matcher2.group(5));
    assertEquals(" anqur x y", matcher2.group(6));
  }
}
