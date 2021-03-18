// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author kiva
 */
public class EmojiTest {
  @Test
  public void ensureJavaAPIIsCorrect() {
    var x = "🐴";
    assertEquals(x.length(), 2);
    assertEquals(x.codePoints().count(), 1);
    assertEquals(x.getBytes(StandardCharsets.UTF_8).length, 4);
    assertTrue(Character.isSurrogate(x.charAt(0)));
    assertTrue(Character.isHighSurrogate(x.charAt(0)));
    assertTrue(Character.isLowSurrogate(x.charAt(1)));
    assertTrue(Character.isSurrogatePair(x.charAt(0), x.charAt(1)));
    assertFalse(Character.isUnicodeIdentifierPart(x.charAt(0)));
    assertFalse(Character.isUnicodeIdentifierPart(x.charAt(1)));
    assertFalse(Character.isUnicodeIdentifierPart(x.codePointAt(0)));
    assertTrue(Character.isSupplementaryCodePoint(x.codePointAt(0)));
  }

  @Test
  public void ensureCJKRange() {
    var x = "我";
    assertEquals(x.length(), 1);
    assertEquals(x.codePoints().count(), 1);
    var i = x.codePointAt(0);
    assertTrue(Character.isUnicodeIdentifierPart(i));
    assertTrue(Character.isSupplementaryCodePoint(i));
  }
}
