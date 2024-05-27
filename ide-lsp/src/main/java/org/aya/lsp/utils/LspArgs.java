// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.utils;

import org.aya.prelude.GeneratedVersion;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "aya-lsp",
  mixinStandardHelpOptions = true,
  version = "Aya Language Server v" + GeneratedVersion.VERSION_STRING,
  showDefaultValues = true)
public class LspArgs {
  @Option(names = {"-m", "--mode"}, description = "Choose which mode to run: " +
    "server mode (listen and wait for connection), client mode (connect to existing port) " +
    "or debug mode (use stdin and stdout).", defaultValue = "server")
  public Mode mode;
  @Option(names = {"-H", "--host"}, description = "Specify hostname.", defaultValue = "localhost")
  public String host;
  @Option(names = {"-p", "--port"}, description = "Specify port.", defaultValue = "11451")
  public int port;

  public enum Mode {
    server,
    client,
    debug,
  }
}
