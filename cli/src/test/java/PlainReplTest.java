// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import org.aya.cli.repl.Repl;
import org.aya.cli.repl.ReplConfig;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PlainReplTest {
  public static final @NotNull Path testConfig = Paths.get("build", "test_config.json");

  @Test public void exit() {
    var writer = new StringWriter();
    var repl = new Repl.PlainRepl(new ReplConfig(testConfig), new StringReader(":exit"), writer);
    repl.run();
    assertNotNull(writer.toString());
  }
}
