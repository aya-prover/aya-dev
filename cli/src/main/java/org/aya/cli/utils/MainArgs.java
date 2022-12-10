// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.utils;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.render.RenderOptions;
import org.aya.prelude.GeneratedVersion;
import org.aya.util.reporter.Problem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
    @Parameters(paramLabel = "<input-file>", description = "File to compile")
    public String inputFile;
    @Option(names = {"-o", "--output"}, description = "Set literate output file")
    public String outputFile;
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
  @Option(names = {"--pretty-stage"}, description = "Pretty print the code in a certain stage." + CANDIDATES)
  public DistillStage prettyStage;
  @Option(names = {"--pretty-format"}, description = "Pretty print format." + CANDIDATES, defaultValue = "markdown")
  public DistillFormat prettyFormat;
  @Option(names = {"--pretty-dir"}, description = "Specify output directory of pretty printing.", defaultValue = ".")
  public String prettyDir;
  @Option(names = {"--pretty-color"}, description = "The color theme of pretty printing." + CANDIDATES, defaultValue = "emacs")
  public PredefinedStyle prettyColor;
  @Option(names = {"--pretty-no-code-style"}, description = "Do not render styled code.")
  public boolean prettyNoCodeStyle;
  @Option(names = {"--trace"}, description = "Enable tracing.")
  public boolean enableTrace;
  @Option(names = {"--ascii-only"}, description = "Do not show unicode in success/fail message.")
  public boolean asciiOnly;
  @Option(names = {"--module-path"}, description = "Search for module under this path.")
  public List<String> modulePaths;
  @Option(names = {"--verbosity", "-v"}, description = "Minimum severity of error reported." + CANDIDATES, defaultValue = "WARN")
  public Problem.Severity verbosity;

  @CommandLine.ArgGroup
  public Action action;

  public ImmutableSeq<String> modulePaths() {
    return modulePaths == null ? ImmutableSeq.empty() : ImmutableSeq.from(modulePaths);
  }

  public enum PredefinedStyle {
    emacs,
    intellij,
  }

  public enum DistillStage {
    raw,
    scoped,
    typed,
    literate,
  }

  public enum DistillFormat {
    html(RenderOptions.OutputTarget.HTML),
    plain(RenderOptions.OutputTarget.Plain),
    latex(RenderOptions.OutputTarget.LaTeX),
    markdown(RenderOptions.OutputTarget.AyaMd),
    unix(RenderOptions.OutputTarget.Terminal);

    public final @NotNull RenderOptions.OutputTarget target;

    DistillFormat(RenderOptions.@NotNull OutputTarget target) {
      this.target = target;
    }
  }

  public enum ReplType {
    plain,
    jline
  }
}
