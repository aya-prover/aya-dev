// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.repl.AyaRepl;
import org.aya.cli.repl.ReplConfig;
import org.aya.cli.single.CliReporter;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.cli.utils.MainArgs;
import org.aya.tyck.trace.MdUnicodeTrace;
import org.aya.tyck.trace.Trace;
import picocli.CommandLine;

import java.nio.file.Paths;
import java.util.concurrent.Callable;

public class Main extends MainArgs implements Callable<Integer> {
  public static void main(String... args) {
    int exitCode = new CommandLine(new Main()).execute(args);
    System.exit(exitCode);
  }

  @Override public Integer call() throws Exception {
    if (action == null) {
      System.err.println("Try `aya --help` to see available commands");
      return 1;
    }
    if (action.repl != null) return AyaRepl.start(modulePaths().map(Paths::get), action.repl);
    var message = asciiOnly
      ? CompilerFlags.Message.ASCII
      : CompilerFlags.Message.EMOJI;
    var inputFile = action.compile.inputFile;
    var outputFile = action.compile.outputFile;
    var filePath = Paths.get(inputFile);
    var outputPath = outputFile == null ? null : Paths.get(outputFile);
    var distillOptions = ReplConfig.loadFromDefault().distillerOptions;
    var reporter = CliReporter.stdio(!asciiOnly, distillOptions, verbosity);
    var distillation = prettyStage != null ? new CompilerFlags.DistillInfo(
      prettyStage,
      prettyFormat,
      Paths.get(prettyDir != null ? prettyDir : ".")
    ) : null;
    var flags = new CompilerFlags(message, interruptedTrace,
      action.compile.isRemake, distillation,
      modulePaths().view().map(Paths::get),
      outputPath);

    if (action.compile.isLibrary || action.compile.isRemake) {
      // TODO: move to a new tool
      return LibraryCompiler.compile(reporter, flags, filePath);
    }
    var traceBuilder = enableTrace ? new Trace.Builder() : null;
    var compiler = new SingleFileCompiler(reporter, null, traceBuilder, distillOptions);
    var status = compiler.compile(filePath, flags, null);
    if (traceBuilder != null)
      System.err.println(new MdUnicodeTrace(2, distillOptions)
        .docify(traceBuilder).debugRender());
    return status;
  }
}
