// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli.library.json;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import kala.collection.immutable.ImmutableSeq;
import org.aya.util.Version;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
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
  public String version;
  public Map<String, LibraryDependencyData> dependency;

  private void checkDeserialization() throws JsonParseException {
    try {
      for (var f : getClass().getDeclaredFields())
        if (f.get(this) == null) throw new JsonParseException("Field " + f.getName() + " was not initialized.");
    } catch (IllegalAccessException ignored) {
    }
  }

  private @NotNull LibraryConfig asConfig(@NotNull Path libraryRoot) throws JsonParseException {
    checkDeserialization();
    return asConfig(libraryRoot, config -> libraryRoot.resolve("build"));
  }

  private @NotNull LibraryConfig asConfig(@NotNull Path libraryRoot, @NotNull Function<String, Path> buildRootGen) {
    var buildRoot = buildRootGen.apply(version);
    return new LibraryConfig(
      Version.create(ayaVersion),
      name,
      version,
      libraryRoot,
      libraryRoot.resolve("src"),
      buildRoot,
      buildRoot.resolve("out"),
      ImmutableSeq.from(dependency.entrySet()).view()
        .map(e -> e.getValue().as(e.getKey()))
        .toImmutableSeq()
    );
  }

  private static @NotNull LibraryConfigData fromJson(@NotNull Reader jsonReader) throws JsonParseException {
    return new Gson().fromJson(jsonReader, LibraryConfigData.class);
  }

  private static @NotNull LibraryConfigData of(@NotNull Path root) throws IOException {
    var descriptionFile = root.resolve("aya.json");
    return fromJson(Files.newBufferedReader(descriptionFile));
  }

  public static @NotNull LibraryConfig fromLibraryRoot(@NotNull Path libraryRoot) throws IOException, JsonParseException {
    return of(libraryRoot).asConfig(libraryRoot.normalize());
  }

  public static @NotNull LibraryConfig fromDependencyRoot(@NotNull Path dependencyRoot, @NotNull Function<String, Path> buildRoot) throws IOException, JsonParseException {
    return of(dependencyRoot).asConfig(dependencyRoot.normalize(), buildRoot);
  }
}
