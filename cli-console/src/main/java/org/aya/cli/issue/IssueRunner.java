// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.issue;

import kala.collection.immutable.ImmutableSeq;
import org.aya.util.position.SourceFile;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class IssueRunner {
  public static int run(@NotNull SourceFile file, @NotNull Path testDir, @NotNull Reporter reporter) throws IOException {
    var result = new IssueParser(file, reporter).parse();

    if (result == null) {
      System.err.println("Issue tracker is not enabled.");
      return -1;
    }

    var files = result.files();
    var version = result.ayaVersion();

    IssueRunner.setup(files, testDir);
    System.out.println(version == null ? null : version.versionNumber());
    System.out.println(files.joinToString(" ", (f) -> {
      if (f.name() == null) return "<unnamed>.aya";
      return f.name();
    }));
    return 0;
  }

  public static void setup(@NotNull ImmutableSeq<IssueParser.File> files, @NotNull Path testDir) throws IOException {
    var sourceRoot = testDir.resolve("src");
    sourceRoot.toFile().mkdirs();

    files.forEachChecked(file -> {
      var fileName = file.name();
      if (fileName == null) fileName = UUID.randomUUID().toString().replace("-", "") + ".aya";

      var path = Path.of(fileName);
      var theFile = sourceRoot.resolve(path);
      theFile.getParent().toFile().mkdirs();
      Files.writeString(theFile, file.content());
    });

    var ayaJson = testDir.resolve("aya.json");
    Files.writeString(ayaJson, """
      {
        "ayaVersion": "114514",
        "group": "org.aya-prover",
        "name" : "issue-checker",
        "version": "0.1.0"
      }
      """);
  }
}
