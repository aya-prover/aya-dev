// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.experiments;

import org.aya.concrete.remark.CodeAttrProcessor;
import org.commonmark.parser.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CommonMark {
  @Test public void backend() {
    var parser = Parser.builder().customDelimiterProcessor(CodeAttrProcessor.INSTANCE).build();
    var node = parser.parse("hey `ast`{show=type, implicit=false}");
    assertNotNull(node);
    // System.out.println(node);
  }
}
