// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli.library;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import org.aya.util.Version;
import kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.List;

/**
 * @author ice1000
 * @apiNote for GSON.
 * @see LibraryData#as()
 * @see LibraryConfig
 */
public final class LibraryData {
  public String ayaVersion;
  public String outDir;
  public List<String> srcDirs;
  public List<String> libraryPaths;

  public void checkDeserialization() throws JsonParseException {
    //noinspection CatchMayIgnoreException
    try {
      for (Field f : getClass().getDeclaredFields())
        if (f.get(this) == null) throw new JsonParseException("Field " + f.getName() + " was not initialized.");
    } catch (IllegalAccessException impossible) {
    }
  }

  public @NotNull LibraryConfig as() throws JsonParseException {
    checkDeserialization();
    return new LibraryConfig(Version.create(ayaVersion), Paths.get(outDir),
      ImmutableSeq.from(srcDirs).map(Paths::get),
      ImmutableSeq.from(libraryPaths).map(Paths::get));
  }

  public static @NotNull LibraryConfig fromJson(@NotNull String jsonCode) throws JsonParseException {
    return new Gson().fromJson(jsonCode, LibraryData.class).as();
  }

  public static @NotNull LibraryConfig fromJson(@NotNull Reader jsonReader) throws JsonParseException {
    return new Gson().fromJson(jsonReader, LibraryData.class).as();
  }
}
