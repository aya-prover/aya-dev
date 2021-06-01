// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli;

import org.aya.tyck.trace.MdUnicodeTrace;
import org.aya.tyck.trace.Trace;
import org.glavo.kala.tuple.Unit;
import org.ice1000.jimgui.util.JniLoader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

public class Cli extends CliArgs implements Callable<Integer> {
  @Override
  public Integer call() throws Exception {
    var message = asciiOnly
      ? CompilerFlags.Message.ASCII
      : CompilerFlags.Message.EMOJI;
    var filePath = Paths.get(inputFile);
    var sourceCode = checkAndRead(filePath, inputFile);
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
        new ImGuiTrace(sourceCode).mainLoop(traceBuilder.root());
      }
      case markdown -> {
        var printer = new MdUnicodeTrace();
        traceBuilder.root().forEach(e -> e.accept(printer, Unit.unit()));
        System.err.println(printer.builder);
      }
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
