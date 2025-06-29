// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CompilerTests {

  public static final @NotNull Path TEST_DIR = Paths.get("src", "test", "resources").toAbsolutePath();
  public static final @NotNull Path GEN_DIR = Paths.get("build", "tmp", "testGenerated");


  @Test
  public void testFullFile() throws IOException {
    var files = Files.walk(TEST_DIR.resolve("full-file")).filter(Files::isRegularFile).toList();
    for (var file: files) {
      var test = new FullFileTest(file);
      test.init();
      test.generateOutputDir();
    }
  }
}
