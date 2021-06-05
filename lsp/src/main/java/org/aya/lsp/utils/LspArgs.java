// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.utils;

import org.aya.prelude.GeneratedVersion;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static org.aya.cli.PicocliUtils.CANDIDATES_ON_A_NEW_LINE;

@Command(name = "aya-lsp",
  mixinStandardHelpOptions = true,
  version = "Aya Language Server v" + GeneratedVersion.VERSION_STRING,
  showDefaultValues = true)
public class LspArgs {
  @Option(names = {"-m", "--mode"}, description = "Choose which mode to run: " +
    "server mode (listen and wait for connection), client mode (connect to existing port) " +
    "or debug mode (use stdin and stdout)." + CANDIDATES_ON_A_NEW_LINE, defaultValue = "server")
  public Mode mode;
  @Option(names = {"-H", "--host"}, description = "Specify hostname.", defaultValue = "localhost")
  public String host;
  @Option(names = {"-p", "--port"}, description = "Specify port.")
  public int port;

  public enum Mode {
    server,
    client,
    debug,
  }
}
