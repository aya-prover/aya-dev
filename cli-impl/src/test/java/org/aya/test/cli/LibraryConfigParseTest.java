// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.test.cli;

import org.aya.cli.library.json.LibraryConfigData;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LibraryConfigParseTest {
  @Test public void badJson() {
    assertThrows(LibraryConfigData.BadConfig.class, () -> test("{"));
    assertThrows(LibraryConfigData.BadConfig.class, () -> test("{ \"name\": \"test\", \"version\": \"test\" }"));
    assertThrows(LibraryConfigData.BadConfig.class, () -> test("{ \"name\": \"test\", \"group\": \"test\" }"));
    assertThrows(LibraryConfigData.BadConfig.class, () -> test("{ \"version\": \"test\", \"group\": \"test\" }"));
    assertDoesNotThrow(() -> test("""
      {
        "name": "test",
        "group": "test",
        "version": "test"
      }
      """));
  }

  private void test(@NotNull String content) throws IOException {
    var ayaJson = Files.createTempFile("aya-library-test", ".json");
    Files.writeString(ayaJson, content);
    LibraryConfigData.ofAyaJson(ayaJson).checkDeserialization(ayaJson);
    Files.deleteIfExists(ayaJson);
  }
}
