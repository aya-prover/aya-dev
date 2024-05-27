// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.console;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.utils.CliEnums.PrettyFormat;
import org.aya.cli.utils.CliEnums.PrettyStage;
import org.aya.prelude.GeneratedVersion;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

@Command(name = "aya",
  mixinStandardHelpOptions = true,
  version = "Aya v" + GeneratedVersion.VERSION_STRING,
  descriptionHeading = "%n@|bold,underline Description|@:%n%n",
  parameterListHeading = "%n@|bold,underline Parameters|@:%n",
  optionListHeading = "%n@|bold,underline Options|@:%n",
  showDefaultValues = true)
public class MainArgs {
  public static class ReplAction {
    @Option(names = {"--repl", "--interactive", "-i"}, description = "Start an interactive REPL.", required = true)
    public boolean repl;
    @Option(names = {"--repl-type", "--interactive-type"}, defaultValue = "jline", description =
      "Specify the type of the interactive REPL." + CANDIDATES)
    public ReplType replType;
  }

  public static class CompileAction {
    @Option(names = {"--make"}, description = "Treat input file as a library root")
    public boolean isLibrary;
    @Option(names = {"--remake"}, description =
      "Treat input file as a library root and remake all")
    public boolean isRemake;
    @Option(names = {"--no-code"}, description =
      "Treat input file as a library root (no outputs will be saved to disk)")
    public boolean isNoCode;
  }

  public static class PlctAction {
    @Option(names = {"--plct-report"}, description = "Generate a PLCT monthly report")
    public boolean plctReport;

    @Option(names = {"--plct-report-since"}, description =
      "Override the PLCT report start date", paramLabel = "number of days ago")
    public int reportSince;

    @Option(names = {"--plct-report-repo"}, description =
      "Override the GitHub repository to generate a report for", paramLabel = "owner/repo")
    public String repoName;
  }

  /** Either `repl` or `compile` is not null */
  public static class Action {
    @CommandLine.ArgGroup(heading = "REPL arguments:%n", exclusive = false)
    public @Nullable ReplAction repl;

    @CommandLine.ArgGroup(heading = "Compiler arguments:%n", exclusive = false)
    public @Nullable CompileAction compile;

    @CommandLine.ArgGroup(heading = "PLCT report arguments:%n", exclusive = false)
    public @Nullable PlctAction plct;
  }

  @Option(names = {"--interrupted-trace"}, hidden = true)
  public boolean interruptedTrace;
  /**
   * I couldn't find a simpler way to let Picocli automatically show candidate values.
   * Appending this is a workaround solution.
   */
  public static final @NonNls String CANDIDATES = "\n  Candidates: ${COMPLETION-CANDIDATES}";
  @Option(names = {"--pretty-stage"}, description = "Pretty print the code in a certain stage." + CANDIDATES)
  public PrettyStage prettyStage;
  @Option(names = {"--pretty-format"}, description = "Pretty print format." + CANDIDATES, defaultValue = "markdown")
  public PrettyFormat prettyFormat;
  @Option(names = {"--pretty-dir"}, description = "Specify output directory of pretty printing.")
  public String prettyDir;
  @Option(names = {"--pretty-color"}, description = "The color theme of pretty printing." + CANDIDATES, defaultValue = "emacs")
  public PredefinedStyle prettyColor;
  @Option(names = {"--pretty-no-code-style"}, description = "Do not include default highlight styles.")
  public boolean prettyNoCodeStyle;
  @Option(names = {"--pretty-inline-code-style"}, description = "Use inlined highlight styles.")
  public boolean prettyInlineCodeStyle;
  @Option(names = {"--pretty-ssr"}, description = "Generate Server-Side-Rendering code for literate output.")
  public boolean prettySSR;
  @Option(names = {"--ascii-only"}, description = "Do not show unicode in success/fail message.")
  public boolean asciiOnly;
  @Option(names = {"--module-path"}, description = "Search for module under this path.")
  public List<String> modulePaths;
  @Option(names = {"--verbosity", "-v"}, description = "Minimum severity of error reported." + CANDIDATES, defaultValue = "WARN")
  public Problem.Severity verbosity;
  @Option(names = {"--fake-literate"}, description = "Generate literate output without compiling.")
  public boolean fakeLiterate;

  @Parameters(paramLabel = "<input-file>", defaultValue = "null", description = "File to compile")
  public String inputFile;
  @Option(names = {"-o", "--output"}, description = "Set literate output file")
  public String outputFile;

  @CommandLine.ArgGroup
  public Action action;

  public ImmutableSeq<String> modulePaths() {
    return modulePaths == null ? ImmutableSeq.empty() : ImmutableSeq.from(modulePaths);
  }

  public enum PredefinedStyle {
    emacs,
    intellij,
  }

  public enum ReplType {
    plain,
    jline
  }
}
