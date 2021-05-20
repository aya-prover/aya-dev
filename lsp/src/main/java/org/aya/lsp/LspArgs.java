// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp;

import com.beust.jcommander.Parameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LspArgs {
  @Parameter(names = {"-m", "--mode"}, description = "Choose which mode to run: " +
    "Server mode (listen and wait for connection), Client mode (connect to existing port) " +
    "or Debug mode (use stdin and stdout)")
  public @NotNull Mode mode = Mode.server;
  @Parameter(names = {"-H", "--host"}, description = "Specify hostname")
  public @Nullable String host = "localhost";
  @Parameter(names = {"-p", "--port"}, description = "Specify port")
  public int port;

  @Parameter(names = {"--version"}, description = "Display the current version")
  public boolean version = false;
  @Parameter(names = {"--help", "-h"}, description = "Show this message", help = true)
  public boolean help = false;

  public enum Mode {
    server,
    client,
    debug,
  }
}
