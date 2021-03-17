// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli;

import com.beust.jcommander.Parameter;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CliArgs {
  @Parameter(names = {"--version"}, description = "Display the current version")
  public boolean version = false;
  @Parameter(names = {"--help", "-h"}, description = "Show this message", help = true)
  public boolean help = false;
  @Parameter(names = {"--interrupted-trace"}, hidden = true)
  public boolean interruptedTrace = false;
  @Parameter(names = {"--dump-ast"}, hidden = true)
  public boolean dumpAST = false;
  @Parameter(names = {"--trace"}, description = "Print type checking traces")
  public @Nullable TraceFormat traceFormat;
  @Parameter(names = {"--ascii-only"}, description = "Do not show unicode in success/fail message")
  public boolean asciiOnly;
  @Parameter(names = {"--module-path"}, description = "Search for module under this path")
  public List<String> modulePaths;
  @Parameter(description = "<input-file>")
  public String inputFile;

  public ImmutableSeq<String> modulePaths() {
    return modulePaths == null ? ImmutableSeq.empty() : ImmutableSeq.from(modulePaths);
  }

  public enum TraceFormat {
    ImGui,
    Markdown
  }
}
