// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.repl.Repl;
import org.aya.cli.repl.ReplConfig;
import org.aya.cli.single.CliReporter;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.cli.utils.MainArgs;
import org.aya.tyck.trace.MdUnicodeTrace;
import org.aya.tyck.trace.Trace;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

public class Main extends MainArgs implements Callable<Integer> {
  public static void main(String... args) {
    int exitCode = new CommandLine(new Main()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws Exception {
    if (action == null) return 0;
    if (action.repl != null) return Repl.start(action.repl);
    var message = asciiOnly
      ? CompilerFlags.Message.ASCII
      : CompilerFlags.Message.EMOJI;
    var inputFile = action.compile.inputFile;
    var filePath = Paths.get(inputFile);
    var distillOptions = ReplConfig.loadFromDefault().distillerOptions;
    if (action.compile.isLibrary) {
      // TODO: move to a new tool
      return LibraryCompiler.compile(filePath);
    }
    var traceBuilder = enableTrace ? new Trace.Builder() : null;
    var compiler = new SingleFileCompiler(CliReporter.INSTANCE, null, traceBuilder, distillOptions);
    var distillation = prettyStage != null ? new CompilerFlags.DistillInfo(
      prettyStage,
      prettyFormat,
      Paths.get(prettyDir != null ? prettyDir : ".")
    ) : null;
    var status = compiler.compile(filePath, new CompilerFlags(
      message, interruptedTrace, distillation,
      modulePaths().view().map(Paths::get)), null);
    if (traceBuilder != null)
      System.err.println(new MdUnicodeTrace(2, distillOptions)
        .docify(traceBuilder).debugRender());
    return status;
  }

  private static @NotNull String checkAndRead(@NotNull Path filePath, @NotNull String fileDisplayName) {
    try {
      return Files.readString(filePath);
    } catch (IOException e) {
      System.err.println(fileDisplayName + ": file not found (" + filePath + ")");
    }
    System.exit(1);
    throw new IllegalStateException();
  }
}
