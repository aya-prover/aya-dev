// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.cli;

import com.beust.jcommander.JCommander;
import org.mzi.prelude.GeneratedVersion;

public class Main {
  public static void main(String... args) {
    var cli = new CliArgs();
    JCommander.newBuilder().addObject(cli).build().parse(args);
    if (cli.version) {
      System.out.println("Mzi v" + GeneratedVersion.VERSION_STRING);
      return;
    }
    if (cli.help) return;
    System.out.println("Hello, Aya!");
  }
}
