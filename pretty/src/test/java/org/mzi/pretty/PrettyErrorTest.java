// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.pretty;

import org.junit.jupiter.api.Test;
import org.mzi.pretty.error.PrettyError;
import org.mzi.pretty.error.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author kiva
 */
public class PrettyErrorTest {
  @Test
  public void testLessThan5Line() {
    String code = """
      package org.mzi.pretty
      import org.junit.jupiter.apt.Test
      import org.mzi.pretty.PrettyError
      """;
    var doc = new PrettyError(
      "<stdin>",
      Span.from(code, 48, 50),
      "package 'org.junit.jupiter.apt.Test' not found",
      "Did you mean 'org.junit.jupiter.api.Test'"
    ).toDoc();

    String text = doc.renderWithPageWidth(80);
    System.out.println(text);

    assertEquals("""
      In file <stdin>:2:25 ->\s
            
        1 | package org.mzi.pretty
        2 | import org.junit.jupiter.apt.Test
                                     ^-^
        3 | import org.mzi.pretty.PrettyError
       \s
      Error: package 'org.junit.jupiter.apt.Test' not found
      note: Did you mean 'org.junit.jupiter.api.Test'""", text);
  }
}
