// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Represents a full Aya file for JIT test. Apart from testing for correctness it should also
/// output the following information into its respective subdirectory under `build/tmp/testGenerated`
/// for debugging:
/// - ANF intermediate representation
/// - Optimization pass logs
/// - Generated target code
public class FullFileTest {

  private final @NotNull Path source;
  private final @NotNull String name;
  private String code;

  public FullFileTest(@NotNull Path from) {
    source = from;
    var fileName = from.getFileName().toString();
    int lastDot = fileName.lastIndexOf('.');
    name = (lastDot == -1) ? fileName : fileName.substring(0, lastDot);
  }
  public void init() throws IOException {
    code = Files.readString(source);
    System.out.println("[full-file] Loaded test case: " + name + " (len=" + code.length() + ")");
  }

  public void generateOutputDir() throws IOException {
    CompilerTests.GEN_DIR.resolve(name).toFile().mkdirs();
  }
}
