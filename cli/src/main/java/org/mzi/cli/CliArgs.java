// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.cli;

import com.beust.jcommander.Parameter;

public class CliArgs {
  @Parameter(names = {"--version"}, description = "Display the current version")
  public boolean version = false;
  @Parameter(names = {"--help", "-h"}, description = "Show this message", help = true)
  public boolean help = false;
  @Parameter(names = {"--verbose", "-v"}, description = "Print type checking traces")
  public boolean verbose;
  @Parameter(names = {"--visualize"}, description = "Visualize type checking traces")
  public boolean visualization;
  @Parameter(names = {"--ascii-only"}, description = "Do not show unicode in success/fail message")
  public boolean asciiOnly;
  @Parameter(description = "<input-file>")
  public String inputFile;
}
