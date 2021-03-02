// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.mzi.cli;

import com.beust.jcommander.JCommander;
import org.glavo.kala.tuple.Unit;
import org.mzi.prelude.GeneratedVersion;
import org.mzi.tyck.trace.MdUnicodeTrace;
import org.mzi.tyck.trace.Trace;

import java.io.IOException;
import java.nio.file.Paths;

public class Main {
  public static void main(String... args) throws IOException {
    var cli = new CliArgs();
    var commander = JCommander.newBuilder().addObject(cli).build();
    commander.parse(args);
    if (cli.version) {
      System.out.println("Mzi v" + GeneratedVersion.VERSION_STRING);
      if (cli.inputFile == null) return;
    } else if (cli.help || cli.inputFile == null) {
      commander.usage();
      return;
    }

    var inputFile = cli.inputFile;
    var flags = cli.asciiOnly
      ? CompilerFlags.ASCII_FLAGS
      : CompilerFlags.DEFAULT_FLAGS;
    var filePath = Paths.get(inputFile);
    var traceBuilder = cli.verbose ? new Trace.Builder() : null;
    var compiler = new SingleFileCompiler(new CliReporter(filePath), filePath, traceBuilder);
    var status = compiler.compile(flags);
    if (traceBuilder != null) {
      var printer = new MdUnicodeTrace();
      traceBuilder.root().forEach(e -> e.accept(printer, Unit.unit()));
      System.err.println(printer.builder);
    }
    System.exit(status);
  }
}
