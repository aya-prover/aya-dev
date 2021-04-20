// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import org.aya.prelude.GeneratedVersion;
import org.aya.tyck.trace.MdUnicodeTrace;
import org.aya.tyck.trace.Trace;
import org.glavo.kala.tuple.Unit;
import org.ice1000.jimgui.util.JniLoader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class Main {
  public static void main(String... args) throws IOException {
    var cli = new CliArgs();
    var commander = JCommander.newBuilder().addObject(cli).build();
    try {
      commander.parse(args);
    } catch (ParameterException e) {
      System.err.println(e.getLocalizedMessage());
      System.exit(-1);
    }
    if (cli.version) {
      System.out.println("Aya v" + GeneratedVersion.VERSION_STRING);
      if (cli.inputFile == null) return;
    } else if (cli.help || cli.inputFile == null) {
      commander.usage();
      return;
    }

    var inputFile = cli.inputFile;
    var message = cli.asciiOnly
      ? CompilerFlags.Message.ASCII
      : CompilerFlags.Message.EMOJI;
    var filePath = Paths.get(inputFile);
    var sourceCode = checkAndRead(filePath, inputFile);
    var traceBuilder = cli.traceFormat != null ? new Trace.Builder() : null;
    var reporter = new CliReporter();
    var compiler = new SingleFileCompiler(reporter, null, traceBuilder);
    var distillation = cli.prettyStage != null ? new CompilerFlags.DistillInfo(
      cli.prettyStage,
      cli.prettyFormat,
      Paths.get(cli.prettyDir != null ? cli.prettyDir : ".")
    ) : null;
    var status = compiler.compile(filePath, new CompilerFlags(
      message,
      cli.interruptedTrace,
      distillation,
      cli.modulePaths().view().map(Paths::get)));
    if (traceBuilder != null) switch (cli.traceFormat) {
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
    System.exit(status);
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
