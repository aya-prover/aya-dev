// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.pretty;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

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

  private boolean isVariationSelector(int code) {
    return code >= (int) '\uFE00' && code <= (int) '\uFE0F';
  }

  @Test
  public void emoji64() {
    List.of("❤️", "⬆️", "⬇️", "⬅️", "➡️").forEach(x -> {
      System.out.println(x);
      assertEquals(x.length(), 2);
      assertEquals(x.codePoints().count(), 2);
      assertEquals(x.getBytes(StandardCharsets.UTF_8).length, 6);
      assertFalse(Character.isSurrogate(x.charAt(0)));
      assertFalse(Character.isSurrogate(x.charAt(1)));
      assertFalse(Character.isHighSurrogate(x.charAt(0)));
      assertFalse(Character.isLowSurrogate(x.charAt(1)));
      var c1 = x.codePointAt(0);
      var c2 = x.codePointAt(1);
      System.out.println(c1);
      System.out.println(c2);
      assertTrue(isVariationSelector(c2));
      assertFalse(Character.isSupplementaryCodePoint(c1));
      assertFalse(Character.isSupplementaryCodePoint(c2));
      assertFalse(Character.isUnicodeIdentifierPart(c1));
      assertTrue(Character.isUnicodeIdentifierPart(c2));
      assertFalse(Character.isUnicodeIdentifierStart(c1));
      assertFalse(Character.isUnicodeIdentifierStart(c2));
    });
  }

  @Test
  public void ensureCJKRange() {
    var x = "我";
    assertEquals(x.length(), 1);
    assertEquals(x.codePoints().count(), 1);
    var i = x.codePointAt(0);
    assertTrue(Character.isUnicodeIdentifierPart(i));
    assertFalse(Character.isSupplementaryCodePoint(i));
  }
}
