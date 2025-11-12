// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.console;

import org.aya.cli.interactive.ReplConfig;
import org.aya.cli.issue.IssueSetup;
import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.library.incremental.CompilerAdvisor;
import org.aya.cli.literate.FlclFaithfulPrettier;
import org.aya.cli.plct.PLCTReport;
import org.aya.cli.render.RenderOptions;
import org.aya.cli.repl.AyaRepl;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.cli.utils.CliEnums;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.producer.flcl.FlclParser;
import org.aya.states.primitive.PrimFactory;
import org.aya.util.FileUtil;
import org.aya.util.position.SourceFile;
import org.aya.util.position.SourceFileLocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

public class Main extends MainArgs implements Callable<Integer> {
  static void main(String... args) {
    System.exit(new CommandLine(new Main()).execute(args));
  }

  @Override public Integer call() throws Exception {
    if ("null".equals(inputFile)) inputFile = null;
    if (action != null) {
      if (action.repl != null)
        return AyaRepl.start(modulePaths().map(Paths::get), !noPrelude, inputFile, action.repl);
      if (action.plct != null)
        return new PLCTReport().run(action.plct);
    }
    if (inputFile == null) {
      System.err.println("No input file specified");
      return 1;
    }
    if (fakeLiterate) return doFakeLiterate();
    if (setupIssue) return doSetupIssue();
    CompileAction compileAction;
    if (action == null || action.compile == null) compileAction = new CompileAction();
    else compileAction = action.compile;
    return doCompile(compileAction);
  }

  private int doFakeLiterate() throws IOException {
    var replConfig = ReplConfig.loadFromDefault();
    replConfig.loadPrelude = !noPrelude;
    var prettierOptions = replConfig.literatePrettier.prettierOptions;
    var reporter = AnsiReporter.stdio(!noColor, !asciiOnly, prettierOptions, verbosity);
    var renderOptions = createRenderOptions(replConfig);
    var outputPath = outputFile != null ? Paths.get(outputFile) : null;
    // Force it to have a pretty stage so info != null
    prettyStage = CliEnums.PrettyStage.literate;
    var info = computePrettyInfo(outputPath, renderOptions, prettierOptions);
    assert info != null;
    replConfig.close();
    var path = Paths.get(inputFile);
    var file = SourceFile.from(SourceFileLocator.EMPTY, path);
    var doc = new FlclFaithfulPrettier(prettierOptions).highlight(
      new FlclParser(reporter, file).computeAst());
    // Garbage code
    var setup = info.backendOpts(false);
    var output = renderOptions.render(prettyFormat.target, doc, setup);
    if (outputPath != null) FileUtil.writeString(outputPath, output);
    else System.out.println(output);
    return 0;
  }

  private int doCompile(@NotNull CompileAction compile) throws IOException {
    var message = asciiOnly
      ? CompilerFlags.Message.ASCII
      : CompilerFlags.Message.EMOJI;
    var filePath = Paths.get(inputFile);
    var outputPath = outputFile == null ? null : Paths.get(outputFile);
    var replConfig = ReplConfig.loadFromDefault();
    replConfig.loadPrelude = !noPrelude;
    var prettierOptions = replConfig.literatePrettier.prettierOptions;
    var reporter = AnsiReporter.stdio(!noColor, !asciiOnly, prettierOptions, verbosity);
    var renderOptions = createRenderOptions(replConfig);
    replConfig.close();
    var pretty = computePrettyInfo(outputPath, renderOptions, prettierOptions);
    var flags = new CompilerFlags(message, interruptedTrace,
      compile.isRemake, pretty,
      modulePaths().view().map(Paths::get),
      outputPath);

    if (compile.isLibrary || compile.isRemake || compile.isNoCode) {
      var advisor = compile.isNoCode ? CompilerAdvisor.inMemory() : CompilerAdvisor.onDisk();
      return LibraryCompiler.compile(new PrimFactory(), reporter, flags, advisor, filePath);
    }
    var compiler = new SingleFileCompiler(reporter, flags, null);
    if (Files.notExists(filePath)) {
      System.err.println("File not found: " + filePath);
      return -1;
    }
    return compiler.compile(filePath, null);
  }

  private int doSetupIssue() throws IOException {
    var replConfig = ReplConfig.loadFromDefault();
    replConfig.loadPrelude = !noPrelude;
    var prettierOptions = replConfig.literatePrettier.prettierOptions;
    var reporter = AnsiReporter.stdio(!noColor, !asciiOnly, prettierOptions, verbosity);
    replConfig.close();

    if (outputFile == null) outputFile = ".";
    return IssueSetup.run(SourceFile.from(SourceFileLocator.EMPTY, Path.of(inputFile)), Path.of(outputFile), reporter);
  }

  private @Nullable CompilerFlags.PrettyInfo computePrettyInfo(
    @Nullable Path outputPath,
    RenderOptions renderOptions, AyaPrettierOptions prettierOptions
  ) {
    if (prettyStage == null)
      return outputPath != null ? CompilerFlags.prettyInfoFromOutput(
        outputPath, renderOptions, prettyNoCodeStyle, prettyInlineCodeStyle, prettySSR) : null;
    return new CompilerFlags.PrettyInfo(
      asciiOnly,
      prettyNoCodeStyle,
      prettyInlineCodeStyle,
      prettySSR,
      prettyStage,
      prettyFormat,
      prettierOptions, renderOptions,
      datetimeFrontMatterKey, datetimeFrontMatterValue, prettyDir
    );
  }

  private @NotNull RenderOptions createRenderOptions(@NotNull ReplConfig replConfig) {
    var renderOptions = replConfig.literatePrettier.renderOptions;
    switch (prettyColor) {
      case emacs -> renderOptions.colorScheme = RenderOptions.ColorSchemeName.Emacs;
      case intellij -> renderOptions.colorScheme = RenderOptions.ColorSchemeName.IntelliJ;
      case null -> { }
    }
    return renderOptions;
  }
}
