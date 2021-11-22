// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.utils;

import kala.collection.immutable.ImmutableSeq;
import org.aya.prelude.GeneratedVersion;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

import static org.aya.cli.utils.PicocliUtils.CANDIDATES;

@Command(name = "aya",
  mixinStandardHelpOptions = true,
  version = "Aya v" + GeneratedVersion.VERSION_STRING,
  showDefaultValues = true)
public class MainArgs {
  public static class ReplAction {
    @Option(names = {"--repl", "--interactive", "-i"}, description = "Start an interactive REPL.", required = true)
    public boolean repl;
    @Option(names = {"--repl-type", "--interactive-type"}, description = "Specify the type of the interactive REPL." + CANDIDATES, defaultValue = "jline")
    public ReplType replType;
  }

  public static class CompileAction {
    @Option(names = {"--make"}, description = "Treat input file as a library root")
    public boolean isLibrary;
    @Parameters(paramLabel = "<input-file>", description = "File to compile")
    public String inputFile;
    @Option(names = {"-o", "--output"}, description = "Set output file")
    public String outputFile;
  }

  // only one of `repl` and `compile` is not null
  public static class Action {
    @CommandLine.ArgGroup(exclusive = false, heading = "REPL arguments:%n")
    public ReplAction repl;

    @CommandLine.ArgGroup(exclusive = false, heading = "Compiler arguments:%n")
    public CompileAction compile;
  }

  @Option(names = {"--interrupted-trace"}, hidden = true)
  public boolean interruptedTrace;
  @Option(names = {"--pretty-stage"}, description = "Pretty print the code in a certain stage." + CANDIDATES)
  public DistillStage prettyStage;
  @Option(names = {"--pretty-format"}, description = "Pretty print format." + CANDIDATES, defaultValue = "html")
  public DistillFormat prettyFormat;
  @Option(names = {"--pretty-dir"}, description = "Specify output directory of pretty printing.")
  public String prettyDir;
  @Option(names = {"--trace"}, description = "Enable tracing.")
  public boolean enableTrace;
  @Option(names = {"--ascii-only"}, description = "Do not show unicode in success/fail message.")
  public boolean asciiOnly;
  @Option(names = {"--module-path"}, description = "Search for module under this path.")
  public List<String> modulePaths;
  @CommandLine.ArgGroup
  public Action action;

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

  public enum ReplType {
    plain,
    jline
  }
}
