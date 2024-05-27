// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.json;

import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * @author kiva
 * @apiNote for GSON.
 */
public class LibraryDependencyData {
  public @Nullable String version;
  public @Nullable String github;
  public @Nullable String file;

  public @NotNull LibraryDependency as(@NotNull Path libraryRoot, @NotNull String depName) {
    if (version != null) return new LibraryDependency.DepVersion(depName, version);
    if (github != null) return new LibraryDependency.DepGithub(depName, github);
    if (file != null) return new LibraryDependency.DepFile(depName, libraryRoot.resolve(file));
    throw new Panic("Unsupported dependency type for " + depName);
  }
}
