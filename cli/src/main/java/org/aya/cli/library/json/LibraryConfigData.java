// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.json;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.Constants;
import org.aya.prelude.GeneratedVersion;
import org.aya.util.FileUtil;
import org.aya.util.Version;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

/**
 * The library description file (aya.json) with user definable settings.
 *
 * @author ice1000, kiva
 * @apiNote for GSON.
 * @see LibraryConfigData#asConfig(Path)
 * @see LibraryConfig
 */
public final class LibraryConfigData {
  public String ayaVersion;
  public String name;
  public String group;
  public String version;
  public Map<String, LibraryDependencyData> dependency;

  private void checkDeserialization(@NotNull Path libraryRoot) {
    if (ayaVersion == null) ayaVersion = GeneratedVersion.VERSION_STRING;
    if (name == null) throw new BadConfig("Missing `name` in " + libraryRoot);
    if (group == null) throw new BadConfig("Missing `group` in " + libraryRoot);
    if (version == null) throw new BadConfig("Missing `version in " + libraryRoot);
    if (dependency == null) dependency = Map.of();
  }

  private @NotNull LibraryConfig asConfig(@NotNull Path libraryRoot) throws JsonParseException {
    checkDeserialization(libraryRoot.resolve(Constants.AYA_JSON));
    return asConfig(libraryRoot, config -> libraryRoot.resolve("build"));
  }

  private @NotNull LibraryConfig asConfig(@NotNull Path libraryRoot, @NotNull Function<String, Path> buildRootGen) {
    checkDeserialization(libraryRoot.resolve(Constants.AYA_JSON));
    var buildRoot = FileUtil.canonicalize(buildRootGen.apply(version));
    return new LibraryConfig(
      Version.create(ayaVersion),
      name,
      version,
      libraryRoot,
      libraryRoot.resolve("src"),
      buildRoot,
      buildRoot.resolve("out"),
      ImmutableSeq.from(dependency.entrySet()).view()
        .map(e -> e.getValue().as(libraryRoot, e.getKey()))
        .toImmutableSeq()
    );
  }

  private static @NotNull LibraryConfigData of(@NotNull Path root) throws BadConfig, IOException {
    var ayaJson = root.resolve(Constants.AYA_JSON);
    try (var jsonReader = Files.newBufferedReader(ayaJson)) {
      return new Gson().fromJson(jsonReader, LibraryConfigData.class);
    } catch (JsonParseException cause) {
      throw new BadConfig("Failed to parse " + ayaJson, cause);
    }
  }

  public static @NotNull LibraryConfig fromLibraryRoot(@NotNull Path libraryRoot) throws IOException, BadConfig {
    var canonicalPath = FileUtil.canonicalize(libraryRoot);
    return of(canonicalPath).asConfig(canonicalPath);
  }

  public static @NotNull LibraryConfig fromDependencyRoot(@NotNull Path dependencyRoot, @NotNull Function<String, Path> buildRoot) throws IOException, BadConfig {
    var canonicalPath = FileUtil.canonicalize(dependencyRoot);
    return of(canonicalPath).asConfig(canonicalPath, buildRoot);
  }

  public static class BadConfig extends RuntimeException {
    public BadConfig(@NotNull String message) {
      super(message);
    }

    public BadConfig(@NotNull String message, @NotNull Throwable cause) {
      super(message, cause);
    }
  }
}
