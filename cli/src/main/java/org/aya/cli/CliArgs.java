// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli;

import com.beust.jcommander.Parameter;
import org.jetbrains.annotations.Nullable;

public class CliArgs {
  @Parameter(names = {"--version"}, description = "Display the current version")
  public boolean version = false;
  @Parameter(names = {"--help", "-h"}, description = "Show this message", help = true)
  public boolean help = false;
  @Parameter(names = {"--trace"}, description = "Print type checking traces")
  public @Nullable TraceFormat traceFormat;
  @Parameter(names = {"--ascii-only"}, description = "Do not show unicode in success/fail message")
  public boolean asciiOnly;
  @Parameter(description = "<input-file>")
  public String inputFile;

  public enum TraceFormat {
    ImGui,
    Markdown
  }
}
