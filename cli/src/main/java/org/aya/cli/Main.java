// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.library.incremental.CompilerAdvisor;
import org.aya.cli.plct.PLCTReport;
import org.aya.cli.render.RenderOptions;
import org.aya.cli.repl.AyaRepl;
import org.aya.cli.repl.ReplConfig;
import org.aya.cli.single.CliReporter;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.cli.utils.MainArgs;
import org.aya.core.def.PrimDef;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.pretty.printer.PrinterConfig;
import org.aya.tyck.trace.MarkdownTrace;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;

import java.io.IOException;
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
    assert action.compile != null;
    return doCompile(action.compile);
  }

  private int doCompile(@NotNull CompileAction compile) throws IOException {
    var message = asciiOnly
      ? CompilerFlags.Message.ASCII
      : CompilerFlags.Message.EMOJI;
    var inputFile = compile.inputFile;
    var outputFile = compile.outputFile;
    var filePath = Paths.get(inputFile);
    var outputPath = outputFile == null ? null : Paths.get(outputFile);
    var replConfig = ReplConfig.loadFromDefault();
    var prettierOptions = replConfig.prettierOptions;
    var reporter = CliReporter.stdio(!asciiOnly, prettierOptions, verbosity);
    var renderOptions = replConfig.renderOptions;
    switch (prettyColor) {
      case emacs -> renderOptions.colorScheme = RenderOptions.ColorSchemeName.Emacs;
      case intellij -> renderOptions.colorScheme = RenderOptions.ColorSchemeName.IntelliJ;
      case null -> {}
    }
    replConfig.close();
    var pretty = prettyStage == null
      ? (outputPath != null ? prettyInfoFromOutput(outputPath, renderOptions, prettyNoCodeStyle) : null)
      : new CompilerFlags.PrettyInfo(
        asciiOnly,
        prettyNoCodeStyle,
        prettyStage,
        prettyFormat,
        prettierOptions,
        renderOptions,
        prettyDir
      );
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
    var status = compiler.compile(filePath, flags, null);
    if (traceBuilder != null)
      System.err.println(new MarkdownTrace(2, prettierOptions, asciiOnly)
        .docify(traceBuilder).renderToString(PrinterConfig.INFINITE_SIZE, !asciiOnly));
    return status;
  }

  public static @NotNull MainArgs.PrettyFormat detectFormat(@NotNull Path outputFile) {
    var name = outputFile.getFileName().toString();
    if (name.endsWith(".md")) return PrettyFormat.markdown;
    if (name.endsWith(".tex")) return PrettyFormat.latex;
    if (name.endsWith(".html")) return PrettyFormat.html;
    return PrettyFormat.plain;
  }

  public static @Nullable CompilerFlags.PrettyInfo prettyInfoFromOutput(
    @Nullable Path outputFile,
    @NotNull RenderOptions renderOptions,
    boolean noCodeStyle
  ) {
    if (outputFile != null) return new CompilerFlags.PrettyInfo(
      false,
      noCodeStyle,
      PrettyStage.literate,
      detectFormat(outputFile),
      AyaPrettierOptions.pretty(),
      renderOptions,
      null);
    return null;
  }
}
