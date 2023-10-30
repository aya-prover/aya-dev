// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.console;

import org.aya.cli.interactive.ReplConfig;
import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.library.incremental.CompilerAdvisor;
import org.aya.cli.literate.FlclFaithfulPrettier;
import org.aya.cli.parse.FlclParser;
import org.aya.cli.plct.PLCTReport;
import org.aya.cli.render.RenderOptions;
import org.aya.cli.repl.AyaRepl;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.cli.utils.CliEnums;
import org.aya.core.def.PrimDef;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.pretty.printer.PrinterConfig;
import org.aya.tyck.trace.MarkdownTrace;
import org.aya.tyck.trace.Trace;
import org.aya.util.error.SourceFile;
import org.aya.util.error.SourceFileLocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

public class Main extends MainArgs implements Callable<Integer> {
  public static void main(String... args) {
    System.exit(new CommandLine(new Main()).execute(args));
  }

  @Override public Integer call() throws Exception {
    if (action == null) {
      System.err.println("Try `aya --help` to see available commands");
      return 1;
    }
    if (action.repl != null)
      return AyaRepl.start(modulePaths().map(Paths::get), action.repl);
    if (action.plct != null)
      return new PLCTReport().run(action.plct);
    if (action.fileIO == null) {
      System.err.println("Missing <input-files> arguments");
      return 1;
    }
    if (fakeLiterate) return doFakeLiterate(action.fileIO);
    if (action.compile == null) action.compile = new CompileAction();
    return doCompile(action.compile, action.fileIO);
  }

  private int doFakeLiterate(@NotNull FileIO filePath) throws IOException {
    var replConfig = ReplConfig.loadFromDefault();
    var prettierOptions = replConfig.literatePrettier.prettierOptions;
    var reporter = AnsiReporter.stdio(!asciiOnly, prettierOptions, verbosity);
    var renderOptions = createRenderOptions(replConfig);
    var outputPath = filePath.outputFile != null ? Paths.get(filePath.outputFile) : null;
    // Force it to have a pretty stage so info != null
    prettyStage = CliEnums.PrettyStage.literate;
    var info = computePrettyInfo(outputPath, renderOptions, prettierOptions);
    assert info != null;
    replConfig.close();
    var path = Paths.get(filePath.inputFile);
    var file = SourceFile.from(SourceFileLocator.EMPTY, path);
    var doc = new FlclFaithfulPrettier(prettierOptions).highlight(
      new FlclParser(reporter, file).computeAst());
    // Garbage code
    var setup = info.backendOpts(false);
    var output = renderOptions.render(RenderOptions.OutputTarget.LaTeX, doc, setup);
    if (outputPath != null) Files.writeString(outputPath, output, StandardCharsets.UTF_8);
    else System.out.println(output);
    return 0;
  }

  private int doCompile(@NotNull CompileAction compile, @NotNull FileIO files) throws IOException {
    var message = asciiOnly
      ? CompilerFlags.Message.ASCII
      : CompilerFlags.Message.EMOJI;
    var inputFile = files.inputFile;
    var outputFile = files.outputFile;
    var filePath = Paths.get(inputFile);
    var outputPath = outputFile == null ? null : Paths.get(outputFile);
    var replConfig = ReplConfig.loadFromDefault();
    var prettierOptions = replConfig.literatePrettier.prettierOptions;
    var reporter = AnsiReporter.stdio(!asciiOnly, prettierOptions, verbosity);
    var renderOptions = createRenderOptions(replConfig);
    replConfig.close();
    var pretty = computePrettyInfo(outputPath, renderOptions, prettierOptions);
    var flags = new CompilerFlags(message, interruptedTrace,
      compile.isRemake, pretty,
      modulePaths().view().map(Paths::get),
      outputPath);

    if (compile.isLibrary || compile.isRemake || compile.isNoCode) {
      // TODO: move to a new tool
      var advisor = compile.isNoCode ? CompilerAdvisor.inMemory() : CompilerAdvisor.onDisk();
      return LibraryCompiler.compile(new PrimDef.Factory(), reporter, flags, advisor, filePath);
    }
    var traceBuilder = enableTrace ? new Trace.Builder() : null;
    var compiler = new SingleFileCompiler(reporter, null, traceBuilder);
    if (Files.notExists(filePath)) {
      System.err.println("File not found: " + filePath);
      return -1;
    }
    var status = compiler.compile(filePath, flags, null);
    if (traceBuilder != null)
      System.err.println(new MarkdownTrace(2, prettierOptions, asciiOnly)
        .docify(traceBuilder).renderToString(PrinterConfig.INFINITE_SIZE, !asciiOnly));
    return status;
  }

  private @Nullable CompilerFlags.PrettyInfo
  computePrettyInfo(
    @Nullable Path outputPath,
    RenderOptions renderOptions, AyaPrettierOptions prettierOptions
  ) {
    return prettyStage == null
      ? (outputPath != null ? CompilerFlags.prettyInfoFromOutput(
      outputPath, renderOptions, prettyNoCodeStyle, prettyInlineCodeStyle, prettySSR) : null)
      : new CompilerFlags.PrettyInfo(
        asciiOnly,
        prettyNoCodeStyle,
        prettyInlineCodeStyle,
        prettySSR,
        prettyStage,
        prettyFormat,
        prettierOptions,
        renderOptions,
        prettyDir
      );
  }

  private @NotNull RenderOptions createRenderOptions(@NotNull ReplConfig replConfig) {
    var renderOptions = replConfig.literatePrettier.renderOptions;
    switch (prettyColor) {
      case emacs -> renderOptions.colorScheme = RenderOptions.ColorSchemeName.Emacs;
      case intellij -> renderOptions.colorScheme = RenderOptions.ColorSchemeName.IntelliJ;
      case null -> {}
    }
    return renderOptions;
  }
}
