// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.issue;

import com.google.gson.GsonBuilder;
import kala.collection.immutable.ImmutableSeq;
import kala.gson.collection.CollectionTypeAdapter;
import org.aya.generic.Constants;
import org.aya.util.position.SourceFile;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class IssueSetup {
  public record Metadata(@Nullable IssueParser.Version version, @NotNull ImmutableSeq<String> files) { }

  public static final @NotNull String UNNAMED = "<unnamed>" + Constants.AYA_POSTFIX;
  public static final @NotNull String METADATA_FILE = "metadata.json";

  public static int run(@NotNull SourceFile file, @NotNull Path testDir, @NotNull Reporter reporter) throws IOException {
    var result = new IssueParser(file, reporter).parse();

    if (result == null) {
      System.err.println("Issue tracker is not enabled.");
      return -1;
    }

    var files = result.files();
    var version = result.ayaVersion();

    IssueSetup.setup(files, testDir);

    var metadata = new Metadata(version, files.map(it -> it.name() == null ? UNNAMED : it.name()));
    var gson = new GsonBuilder()
      .registerTypeAdapterFactory(CollectionTypeAdapter.factory())
      .serializeNulls()
      .disableHtmlEscaping()
      .setPrettyPrinting()
      .create();

    Files.writeString(testDir.resolve(METADATA_FILE), gson.toJson(metadata));

    return 0;
  }

  public static void setup(@NotNull ImmutableSeq<IssueParser.File> files, @NotNull Path testDir) throws IOException {
    var sourceRoot = testDir.resolve("src");
    sourceRoot.toFile().mkdirs();

    files.forEachChecked(file -> {
      var fileName = file.name();
      if (fileName == null) fileName = UUID.randomUUID().toString().replace("-", "") + Constants.AYA_POSTFIX;

      var path = Path.of(fileName);
      var theFile = sourceRoot.resolve(path);
      theFile.getParent().toFile().mkdirs();
      Files.writeString(theFile, file.content());
    });

    var ayaJson = testDir.resolve(Constants.AYA_JSON);
    Files.writeString(ayaJson, """
      {
        "ayaVersion": "114514",
        "group": "org.aya-prover",
        "name" : "issue-tracker",
        "version": "0.1.0"
      }
      """);
  }
}
