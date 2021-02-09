// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.cli;

import com.beust.jcommander.JCommander;
import org.jetbrains.annotations.NotNull;
import org.mzi.concrete.Decl;
import org.mzi.concrete.parse.MziParsing;
import org.mzi.concrete.parse.MziProducer;
import org.mzi.concrete.resolve.context.SimpleContext;
import org.mzi.prelude.GeneratedVersion;
import org.mzi.tyck.ExprTycker;
import org.mzi.tyck.TyckOptions;
import org.mzi.tyck.error.CountingReporter;

import java.io.IOException;
import java.nio.file.Paths;

public class Main {
  public static final @NotNull String TQL = "\uD83D\uDC02\uD83C\uDF7A";
  public static final @NotNull String NMSL = "\uD83D\uDD28";

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

    TyckOptions.VERBOSE = cli.verbose;

    var inputFile = cli.inputFile;
    var filePath = Paths.get(inputFile);
    var reporter = new CountingReporter(new CliReporter(filePath));
    var parser = MziParsing.parser(filePath, reporter);
    var program = MziProducer.INSTANCE.visitProgram(parser.program());
    var context = new SimpleContext();
    try {
      program.forEach(s -> {
        s.desugar();
        s.resolve(context);
      });
      program.forEach(s -> {
        if (s instanceof Decl decl) decl.tyck(reporter);
      });
    } catch (ExprTycker.TyckerException e) {
      e.printStackTrace();
      System.err.println("""
        A type error was discovered during type checking.
        Please report the stacktrace to the developers so a better error handling could be made.
        Don't forget to inform the version of Mzi you're using and attach your code for reproduction.""");
      System.exit(1);
    }
    if (reporter.isEmpty()) System.out.println(TQL);
    else System.err.println(NMSL);
  }
}
