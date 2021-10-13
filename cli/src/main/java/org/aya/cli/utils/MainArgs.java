// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.utils;

import kala.collection.immutable.ImmutableSeq;
import org.aya.prelude.GeneratedVersion;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

import static org.aya.cli.utils.PicocliUtils.CANDIDATES_ON_A_NEW_LINE;

@Command(name = "aya",
  mixinStandardHelpOptions = true,
  version = "Aya v" + GeneratedVersion.VERSION_STRING,
  showDefaultValues = true)
public class MainArgs {
  @Option(names = {"--interrupted-trace"}, hidden = true)
  public boolean interruptedTrace;
  @Option(names = {"--pretty-stage"}, description = "Pretty print the code in a certain stage." + CANDIDATES_ON_A_NEW_LINE)
  public DistillStage prettyStage;
  @Option(names = {"--pretty-format"}, description = "Pretty print format." + CANDIDATES_ON_A_NEW_LINE, defaultValue = "html")
  public DistillFormat prettyFormat;
  @Option(names = {"--pretty-dir"}, description = "Specify output directory of pretty printing.")
  public String prettyDir;
  @Option(names = {"--trace"}, description = "Specify format of printing type checking traces." + CANDIDATES_ON_A_NEW_LINE)
  public TraceFormat traceFormat;
  @Option(names = {"--ascii-only"}, description = "Do not show unicode in success/fail message.")
  public boolean asciiOnly;
  @Option(names = {"--module-path"}, description = "Search for module under this path.")
  public List<String> modulePaths;
  @Option(names = {"--make"}, description = "Compile a library")
  public boolean isLibrary;
  @Option(names = {"--repl", "--interactive", "-i"}, description = "Start an interactive REPL.")
  public boolean repl;
  @Option(names = {"--repl-type", "--interactive-type"}, description = "Specify the type of the interactive REPL." + CANDIDATES_ON_A_NEW_LINE, defaultValue = "jline")
  public ReplType replType;
  @Parameters(paramLabel = "<input-file>", arity = "0..1")
  public String inputFile;

  public ImmutableSeq<String> modulePaths() {
    return modulePaths == null ? ImmutableSeq.empty() : ImmutableSeq.from(modulePaths);
  }

  public enum DistillStage {
    raw,
    scoped,
    typed,
  }

  public enum DistillFormat {
    html,
    plain,
    latex,
    unix,
  }

  public enum TraceFormat {
    imgui,
    markdown,
  }

  public enum ReplType {
    plain,
    jline
  }
}
