// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.cli;

import com.beust.jcommander.JCommander;
import org.mzi.concrete.Decl;
import org.mzi.concrete.parse.MziParsing;
import org.mzi.concrete.parse.MziProducer;
import org.mzi.concrete.resolve.context.SimpleContext;
import org.mzi.prelude.GeneratedVersion;

import java.io.IOException;
import java.nio.file.Paths;

public class Main {
  public static void main(String... args) throws IOException {
    var cli = new CliArgs();
    var commander = JCommander.newBuilder().addObject(cli).build();
    commander.parse(args);
    if (cli.version) {
      System.out.println("Mzi v" + GeneratedVersion.VERSION_STRING);
      return;
    }
    if (cli.help || cli.inputFile == null) {
      commander.usage();
      return;
    }

    var inputFile = cli.inputFile;
    var filePath = Paths.get(inputFile);
    var reporter = new CliReporter(filePath);
    var parser = MziParsing.parser(filePath, reporter);
    var program = MziProducer.INSTANCE.visitProgram(parser.program());
    var context = new SimpleContext();
    program.forEach(s -> {
      s.desugar();
      s.resolve(context);
    });
    program.forEach(s -> {
      if (s instanceof Decl decl) decl.tyck(reporter);
    });
  }
}
