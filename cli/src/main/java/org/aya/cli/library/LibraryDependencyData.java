// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Paths;

/**
 * @author kiva
 * @apiNote for GSON.
 */
public class LibraryDependencyData {
  public @Nullable String version;
  public @Nullable String github;
  public @Nullable String file;

  public @NotNull LibraryDependency as(@NotNull String depName) {
    if (version != null) return new LibraryDependency.DepVersion(depName, version);
    if (github != null) return new LibraryDependency.DepGithub(depName, github);
    if (file != null) return new LibraryDependency.DepFile(depName, Paths.get(file));
    throw new IllegalArgumentException("Unsupported dependency type for " + depName);
  }
}
