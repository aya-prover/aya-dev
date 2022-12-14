// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.experiments;

import org.commonmark.parser.Parser;
import org.junit.jupiter.api.Test;

public class CommonMarkTest {
  public static void main(String[] args) {
    var parser = Parser.builder().build();
    var node = parser.parse("""
      1. what?
      2. Hey!
      
      <!--- comment --->
      
      + What?
        + Oh?
      + Hey!""");
    System.out.println(node);
  }
}
