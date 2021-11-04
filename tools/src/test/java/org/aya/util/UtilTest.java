// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util;

import kala.collection.Seq;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UtilTest {
  @Test public void stringEscape() {
    Assertions.assertEquals("", StringEscapeUtil.escapeStringCharacters(""));
    assertEquals("", StringEscapeUtil.unescapeStringCharacters(""));
    assertEquals("123", StringEscapeUtil.unescapeStringCharacters("123"));
    assertEquals("123", StringEscapeUtil.escapeStringCharacters("123"));
    assertEquals("123\\n\\t\\r", StringEscapeUtil.escapeStringCharacters("123\n\t\r"));
    Seq.of("123\\n\\t\\r", "\\f\\b\\\\", "'").forEach(s ->
      assertEquals(s, StringEscapeUtil.escapeStringCharacters(StringEscapeUtil.unescapeStringCharacters(s))));
    assertEquals("\\\"", StringEscapeUtil.escapeStringCharacters("\""));
    assertEquals("\"", StringEscapeUtil.unescapeStringCharacters("\\\""));
    assertEquals("\u2333", StringEscapeUtil.unescapeStringCharacters("\\u2333"));
    assertEquals("\2\7", StringEscapeUtil.unescapeStringCharacters("\\2\\7"));
  }

  @SuppressWarnings("ResultOfMethodCallIgnored") @Test
  public void version() {
    var version = new Version(114, 514, 1919);
    var version2 = new Version("810", "1926", "817");
    assertTrue(version.compareTo(version2) < 0);
    assertTrue(version2.compareTo(version) > 0);
    assertEquals(0, version.compareTo(new Version(114, 514, 1919)));
    assertEquals("114.514.1919", version.getLongString());
    assertEquals(114, version.major().intValueExact());
    assertEquals("114.514.810", Version.create("114.514.810").toString());
    assertEquals("114.514", Version.create("114.514").toString());
    assertEquals("114514", Version.create("114514").toString());
    assertThrows(IllegalArgumentException.class, () -> Version.create(""));
  }
}
