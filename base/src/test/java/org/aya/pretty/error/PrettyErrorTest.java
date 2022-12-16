// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.pretty.error;


import kala.collection.immutable.ImmutableArray;
import kala.tuple.Tuple2;
import org.aya.pretty.doc.Doc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertLinesMatch;

public class PrettyErrorTest {
  public static final String CODE = "..........\n..........\n..........\n";

  private void testError(boolean unicode, boolean multiline, String target) {
    var error = new PrettyError(
      "<test>",
      multiline
        ? new LineColSpan(CODE, 1, 1, 2, 6)
        : new LineColSpan(CODE, 1, 0, 1, 11),
      Doc.empty(),
      unicode ? PrettyError.FormatConfig.UNICODE : PrettyError.FormatConfig.CLASSIC,
      multiline
        ? ImmutableArray.of()
        : ImmutableArray.of(new Tuple2<>(new LineColSpan(CODE, 1, 1, 1, 6), Doc.plain("this is a test")))
    );
    assertLinesMatch(target.trim().lines(), error.toDoc().debugRender().trim().lines());
  }

  @Test public void inlineUnicodeError() {
    testError(true, false, """
      In file <test>:1:0 ->
            
        1 │     ..........
          │     ╰──────────╯
          │      ╰────╯ this is a test
        2 │     ..........
        3 │     ..........
            """);
  }

  @Test public void inlineNonUnicodeError() {
    testError(false, false, """
      In file <test>:1:0 ->
            
        1 |     ..........
          |     ^----------^
          |      ^----^ this is a test
        2 |     ..........
        3 |     ..........
            """);
  }

  @Test public void multilineUnicodeError() {
    testError(true, true, """
      In file <test>:1:1 ->
            
        1 │   ..........
          │ ╭──╯
        2 │ │ ..........
          │ ╰───────╯
        3 │   ..........
            """);
  }

  @Test public void multilineNoneUnicodeError() {
    testError(false, true, """
      In file <test>:1:1 ->
            
        1 |   ..........
          | +-^^
        2 | | ..........
          | +-^-----^
        3 |   ..........
            """);
  }
}
