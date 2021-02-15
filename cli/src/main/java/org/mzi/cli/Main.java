// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.cli;

import com.beust.jcommander.JCommander;
import org.mzi.prelude.GeneratedVersion;
import org.mzi.tyck.TyckOptions;

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

    TyckOptions.VERBOSE = cli.verbose;

    var inputFile = cli.inputFile;
    var flags = cli.asciiOnly
      ? CompilerFlags.asciiOnlyFlags()
      : CompilerFlags.defaultFlags();
    var filePath = Paths.get(inputFile);
    new SingleFileCompiler(new CliReporter(filePath), filePath).compile(flags);
  }
}
