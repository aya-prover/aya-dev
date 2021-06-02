// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.

import org.aya.cli.CliArgs;
import org.aya.cli.CliArgs.DistillFormat;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

public class ArgsParserTest {
  @Test
  public void version() {
    var cliArgs = new CliArgs();
    var commandLine = new CommandLine(cliArgs);
    commandLine.parseArgs("--version");
    assertTrue(commandLine.isVersionHelpRequested());
  }

  @Test
  public void file() {
    var cliArgs = new CliArgs();
    var commandLine = new CommandLine(cliArgs);
    var s = "boy.aya";
    commandLine.parseArgs(s);
    assertEquals(s, cliArgs.inputFile);
  }

  @Test
  public void fileAfterDoubleDash() {
    var cliArgs = new CliArgs();
    var commandLine = new CommandLine(cliArgs);
    var s = "boy.aya";
    commandLine.parseArgs("--", s);
    assertEquals(s, cliArgs.inputFile);
  }

  @Test
  public void defaultValues() {
    var cliArgs = new CliArgs();
    var commandLine = new CommandLine(cliArgs);
    var s = "boy.aya";
    commandLine.parseArgs(s);
    assertFalse(cliArgs.interruptedTrace);
    assertEquals(DistillFormat.html, cliArgs.prettyFormat);
  }
}
