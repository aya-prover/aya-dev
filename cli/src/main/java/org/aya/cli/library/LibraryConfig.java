// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.cli.library;

import org.aya.util.Version;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The compiler aspect of library description file, with generated settings.
 *
 * @param ayaVersion version used to compile this library
 * @author re-xyr, kiva
 * @implNote <a href="https://github.com/ice1000/aya-prover/issues/491">issue #491</a>
 */
public record LibraryConfig(
  @NotNull Version ayaVersion,
  @NotNull String name,
  @NotNull Path libraryRoot,
  @NotNull Path librarySrcRoot,
  @NotNull Path libraryBuildRoot
) {
  public @NotNull Path depBuildRoot(@NotNull String depName) throws IOException {
    return Files.createDirectories(libraryBuildRoot.resolve("deps").resolve(depName));
  }
}
