import com.beust.jcommander.JCommander;
import org.aya.cli.CliArgs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
public class ArgsParserTest {
  @Test
  public void version() {
    var cli = new CliArgs();
    var commander = JCommander.newBuilder().addObject(cli).build();
    commander.parse("--version");
    assertTrue(cli.version);
  }

  @Test
  public void file() {
    var cli = new CliArgs();
    var commander = JCommander.newBuilder().addObject(cli).build();
    var s = "boy.aya";
    commander.parse(s);
    assertEquals(s, cli.inputFile);
  }
}
