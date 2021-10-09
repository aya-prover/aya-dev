// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.

import org.aya.cli.utils.MainArgs;
import org.aya.cli.utils.MainArgs.DistillFormat;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

public class ArgsParserTest {
  @Test
  public void version() {
    var cliArgs = new MainArgs();
    var commandLine = new CommandLine(cliArgs);
    commandLine.parseArgs("--version");
    assertTrue(commandLine.isVersionHelpRequested());
  }

  @Test
  public void file() {
    var cliArgs = new MainArgs();
    var commandLine = new CommandLine(cliArgs);
    var s = "boy.aya";
    commandLine.parseArgs(s);
    assertEquals(s, cliArgs.inputFile);
  }

  @Test
  public void fileAfterDoubleDash() {
    var cliArgs = new MainArgs();
    var commandLine = new CommandLine(cliArgs);
    var s = "boy.aya";
    commandLine.parseArgs("--", s);
    assertEquals(s, cliArgs.inputFile);
  }

  @Test
  public void defaultValues() {
    var cliArgs = new MainArgs();
    var commandLine = new CommandLine(cliArgs);
    var s = "boy.aya";
    commandLine.parseArgs(s);
    assertFalse(cliArgs.interruptedTrace);
    assertEquals(DistillFormat.html, cliArgs.prettyFormat);
  }
}
