// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli.library;

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
    return new LibraryConfig(
      Version.create(ayaVersion),
      name,
      libraryRoot,
      libraryRoot.resolve("src"),
      libraryRoot.resolve("build"),
      dependency.entrySet().stream().map(e -> e.getValue().as(e.getKey()))
        .collect(ImmutableSeq.factory())
    );
  }

  private static @NotNull LibraryConfigData fromJson(@NotNull Reader jsonReader) throws JsonParseException {
    return new Gson().fromJson(jsonReader, LibraryConfigData.class);
  }

  public static @NotNull LibraryConfig fromLibraryRoot(@NotNull Path libraryRoot) throws IOException, JsonParseException {
    var descriptionFile = libraryRoot.resolve("aya.json");
    var data = fromJson(Files.newBufferedReader(descriptionFile));
    return data.asConfig(libraryRoot);
  }
}
