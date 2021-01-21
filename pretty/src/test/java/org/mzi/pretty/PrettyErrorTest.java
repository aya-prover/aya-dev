// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.pretty;

import org.junit.jupiter.api.Test;
import org.mzi.pretty.doc.Doc;
import org.mzi.pretty.error.PrettyError;
import org.mzi.pretty.error.Span;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author kiva
 */
public class PrettyErrorTest {
  @Test
  public void testWithLongCode() {
    String code = """
      mod tests;
      pub mod i_hate_tests;
      pub mod test1;
      pub mod test2;
      
      pub fn test_with_long_code() -> i32 {
        // congratulations!
        // I finally bought a cat today!
        // I was just so happy so I came online to write some tests!
        // ok. let's back to the test.
        // this is a comment
        // this is another comment
        // this makes sense?
        // this makes no sense.
      }
      
      pub fn this_is_another_test_for_show_more_line() {
        // I should occupy at least 4 lines
        // this is the second line
        // this is the third line
        // ok, I finished
      }
      """;

    var doc = new PrettyError(
      "<stdin>",
      Span.from(code, 71, 357),
      Doc.plain("No, you don't want to write tests"),
      Doc.empty()
    ).toDoc();

    String text = doc.renderWithPageWidth(80);
    System.out.println(text);

    assertEquals("""
      In file <stdin>:6:7 ->\s
            
         4 | pub mod test2;
         5 |\s
         6 | pub fn test_with_long_code() -> i32 {
         7 |   // congratulations!
         8 |   // I finally bought a cat today!
           | ...
        13 |   // this makes sense?
        14 |   // this makes no sense.
        15 | }
       \s
      Error: No, you don't want to write tests
      """, text);
  }

  @Test
  public void testWithShortCode() {
    String code = """
      package org.mzi.pretty
      import org.junit.jupiter.apt.Test
      import org.mzi.pretty.PrettyError
      """;
    var doc = new PrettyError(
      "<stdin>",
      Span.from(code, 48, 50),
      Doc.plain("package 'org.junit.jupiter.apt.Test' not found"),
      Doc.plain("Did you mean 'org.junit.jupiter.api.Test'")
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
      note: Did you mean 'org.junit.jupiter.api.Test'
      """, text);
  }
}
