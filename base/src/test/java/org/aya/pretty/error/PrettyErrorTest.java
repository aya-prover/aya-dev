// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.error;


import kala.collection.immutable.ImmutableArray;
import kala.tuple.Tuple2;
import org.aya.pretty.doc.Doc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PrettyErrorTest {
  public static final String CODE = "..........\n..........\n..........\n";

  private void assertLineByLine(String x, String y) {
    var xLines = x.trim().lines().toList();
    var yLines = y.trim().lines().toList();
    assertEquals(xLines.size(), yLines.size(), "error output does not have same line number after trim");
    var xIter = xLines.listIterator();
    var yIter = yLines.listIterator();
    while (xIter.hasNext()) {
      var xData = xIter.next();
      var yData = yIter.next();
      assertEquals(xData, yData);
    }
  }

  private void testError(boolean unicode, boolean multiline, String target) {
    var error = new PrettyError(
      "<test>",
      multiline
        ? new LineColSpan(CODE, 1, 1, 2, 6)
        : new LineColSpan(CODE, 0, 0, 10, 10),
      Doc.empty(),
      unicode ? PrettyError.FormatConfig.UNICODE : PrettyError.FormatConfig.CLASSIC,
      multiline
        ? ImmutableArray.of()
        : ImmutableArray.of(new Tuple2<Span, Doc>(new LineColSpan(CODE, 1, 1, 1, 6), Doc.plain("this is a test")))
    );
    System.out.println(error.toDoc().debugRender());
    assertLineByLine(target, error.toDoc().debugRender());
  }

  @Test public void inlineUnicodeError() {
    testError(true, false, """
      In file <test>:0:0 ->

         1 │ ..........
           ┝  ╰────╯ this is a test
         2 │ ..........
         3 │ ..........
            """);
  }

  @Test public void inlineNonUnicodeError() {
    testError(false, false, """
      In file <test>:0:0 ->

         1 | ..........
              ^----^ this is a test
         2 | ..........
         3 | ..........
            """);
  }

  @Test public void multilineUnicodeError() {
    testError(true, true, """
      In file <test>:1:1 ->

        1 │ ..........
          ┝  ⮬ Begin of the error
        2 │ ..........
          ┝      ⮬ End of the error
        3 │ ..........
            """);
  }

  @Test public void multilineNoneUnicodeError() {
    testError(false, true, """
      In file <test>:1:1 ->

        1 | ..........
             ^--------^ Begin of the error
        2 | ..........
             ^--------^ End of the error
        3 | ..........
            """);
  }
}
