// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli;

import org.aya.prelude.GeneratedVersion;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

@Command(name = "aya", mixinStandardHelpOptions = true, version = "Aya v" + GeneratedVersion.VERSION_STRING)
public class CliArgs {
  @Option(names = {"--interrupted-trace"}, hidden = true)
  public boolean interruptedTrace;
  @Option(names = {"--pretty-stage"}, description = "Pretty print the code in a certain stage")
  public DistillStage prettyStage;
  @Option(names = {"--pretty-format"}, description = "Pretty print format", defaultValue = "html")
  public DistillFormat prettyFormat;
  @Option(names = {"--pretty-dir"}, description = "Output directory of pretty printing")
  public @Nullable String prettyDir;
  @Option(names = {"--trace"}, description = "Print type checking traces")
  public @Nullable TraceFormat traceFormat;
  @Option(names = {"--ascii-only"}, description = "Do not show unicode in success/fail message")
  public boolean asciiOnly;
  @Option(names = {"--module-path"}, description = "Search for module under this path")
  public List<String> modulePaths;
  @Parameters(paramLabel = "<input-file>")
  public String inputFile;

  public ImmutableSeq<String> modulePaths() {
    return modulePaths == null ? ImmutableSeq.empty() : ImmutableSeq.from(modulePaths);
  }

  public enum DistillStage {
    raw,
    scoped,
    typed
  }

  public enum DistillFormat {
    html,
    latex
  }

  public enum TraceFormat {
    imgui,
    markdown
  }
}
