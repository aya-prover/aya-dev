// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli;

import org.aya.api.distill.DistillerOptions;
import org.aya.cli.library.LibraryCompiler;
import org.aya.cli.single.CliReporter;
import org.aya.cli.single.CompilerFlags;
import org.aya.cli.single.SingleFileCompiler;
import org.aya.cli.utils.ImGuiTrace;
import org.aya.cli.utils.MainArgs;
import org.aya.tyck.trace.MdUnicodeTrace;
import org.aya.tyck.trace.Trace;
import org.ice1000.jimgui.util.JniLoader;
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
    var message = asciiOnly
      ? CompilerFlags.Message.ASCII
      : CompilerFlags.Message.EMOJI;
    var filePath = Paths.get(inputFile);
    if (isLibrary) {
      // TODO: move to a new tool
      return LibraryCompiler.compile(filePath);
    }
    var traceBuilder = traceFormat != null ? new Trace.Builder() : null;
    var compiler = new SingleFileCompiler(CliReporter.INSTANCE, null, traceBuilder);
    var distillation = prettyStage != null ? new CompilerFlags.DistillInfo(
      prettyStage,
      prettyFormat,
      Paths.get(prettyDir != null ? prettyDir : ".")
    ) : null;
    var status = compiler.compile(filePath, new CompilerFlags(
      message, interruptedTrace, distillation,
      modulePaths().view().map(Paths::get)));
    if (traceBuilder != null) switch (traceFormat) {
      case imgui -> {
        JniLoader.load();
        var sourceCode = checkAndRead(filePath, inputFile);
        new ImGuiTrace(sourceCode, DistillerOptions.DEFAULT).mainLoop(traceBuilder.root());
      }
      case markdown -> System.err.println(new MdUnicodeTrace().docify(traceBuilder).debugRender());
    }
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
