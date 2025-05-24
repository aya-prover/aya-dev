// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import org.aya.util.position.SourceFile;
import org.aya.util.position.SourcePos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SourcePosTest {
  @Test
  public void compareTest() {
    var range = new SourcePos(SourceFile.NONE, 0, 0,
      5, 10,
      15, 20);

    var veryBefore = range.compareVisually(0, 0);
    var before = range.compareVisually(5, 9);
    var justContained0 = range.compareVisually(5, 10);
    var contained = range.compareVisually(10, 114514);
    var justContained1 = range.compareVisually(15, 20);
    var after = range.compareVisually(15, 21);
    var veryAfter = range.compareVisually(114, 514);

    assertEquals(-1, veryBefore);
    assertEquals(-1, before);
    assertEquals(0, justContained0);
    assertEquals(0, contained);
    assertEquals(0, justContained1);
    assertEquals(1, after);
    assertEquals(1, veryAfter);
  }
}
