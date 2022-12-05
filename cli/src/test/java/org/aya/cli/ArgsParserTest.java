// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.aya.cli.utils.MainArgs;
import org.aya.cli.utils.MainArgs.DistillFormat;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

public class ArgsParserTest {
  @Test public void version() {
    var commandLine = new CommandLine(new MainArgs());
    commandLine.parseArgs("--version");
    assertTrue(commandLine.isVersionHelpRequested());
  }

  @Test public void file() {
    var cliArgs = new MainArgs();
    var s = "boy.aya";
    new CommandLine(cliArgs).parseArgs(s);
    assertNotNull(cliArgs.action.compile);
    assertNull(cliArgs.action.repl);
    assertEquals(s, cliArgs.action.compile.inputFile);
  }

  @Test public void fileAfterDoubleDash() {
    var cliArgs = new MainArgs();
    var s = "boy.aya";
    new CommandLine(cliArgs).parseArgs("--", s);
    assertNotNull(cliArgs.action.compile);
    assertNull(cliArgs.action.repl);
    assertEquals(s, cliArgs.action.compile.inputFile);
  }

  @Test public void interactiveSwitch() {
    var cliArgs = new MainArgs();
    new CommandLine(cliArgs).parseArgs("-i");
    assertNotNull(cliArgs.action.repl);
    assertNull(cliArgs.action.compile);
    assertTrue(cliArgs.action.repl.repl);
  }

  @Test public void defaultValues() {
    var cliArgs = new MainArgs();
    new CommandLine(cliArgs).parseArgs("boy.aya");
    assertFalse(cliArgs.interruptedTrace);
    assertEquals(DistillFormat.markdown, cliArgs.prettyFormat);
  }
}
