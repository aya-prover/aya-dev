// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import org.aya.api.error.Problem;
import org.aya.prelude.GeneratedVersion;
import org.aya.tyck.trace.MdUnicodeTrace;
import org.aya.tyck.trace.Trace;
import org.glavo.kala.tuple.Unit;
import org.ice1000.jimgui.util.JniLoader;

import java.io.IOException;
import java.nio.file.Paths;

public class Main {
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
    var sourceCode = Problem.readSourceCode(filePath);
    var traceBuilder = cli.traceFormat != null ? new Trace.Builder() : null;
    var reporter = new CliReporter(filePath, sourceCode);
    var compiler = new SingleFileCompiler(reporter, filePath, traceBuilder);
    var status = compiler.compile(new CompilerFlags(
      message,
      cli.interruptedTrace,
      cli.modulePaths().map(Paths::get)));
    if (traceBuilder != null) switch (cli.traceFormat) {
      case ImGui -> {
        JniLoader.load();
        new ImGuiTrace(sourceCode).mainLoop(traceBuilder.root());
      }
      case Markdown -> {
        var printer = new MdUnicodeTrace();
        traceBuilder.root().forEach(e -> e.accept(printer, Unit.unit()));
        System.err.println(printer.builder);
      }
    }
    System.exit(status);
  }
}
