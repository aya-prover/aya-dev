// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.source;

import kala.collection.SeqView;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.json.LibraryConfig;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.json.LibraryDependency;
import org.aya.syntax.AyaFiles;
import org.aya.util.error.SourceFileLocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A library that lives in the file system.
 *
 * @author kiva
 */
public record DiskLibraryOwner(
  @NotNull SourceFileLocator locator,
  @NotNull MutableList<Path> modulePathMut,
  @NotNull MutableList<LibraryOwner> libraryDepsMut,
  @NotNull MutableList<LibrarySource> librarySourcesMut,
  @NotNull LibraryConfig underlyingLibrary
) implements MutableLibraryOwner {
  private static @Nullable LibraryConfig depConfig(@NotNull LibraryConfig config, @NotNull LibraryDependency dep) throws IOException {
    // TODO: test only: dependency resolving should be done in package manager
    if (dep instanceof LibraryDependency.DepFile file)
      return LibraryConfigData.fromDependencyRoot(
        file.depRoot(),
        config.literateConfig(),
        version -> depBuildRoot(config, dep.depName(), version)
      );
    return null;
  }

  private static @NotNull Path depBuildRoot(@NotNull LibraryConfig config, @NotNull String depName, @NotNull String version) {
    return config.libraryBuildRoot().resolve("deps").resolve(depName + "_" + version);
  }

  public static @NotNull DiskLibraryOwner from(@NotNull LibraryConfig config) throws IOException {
    var srcRoot = config.librarySrcRoot();
    var locator = new SourceFileLocator.Module(SeqView.of(srcRoot));
    var owner = new DiskLibraryOwner(locator, MutableList.create(),
      MutableList.create(), MutableList.create(), config);
    owner.librarySourcesMut.appendAll(AyaFiles.collectAyaSourceFiles(srcRoot)
      .map(p -> LibrarySource.create(owner, p)));
    for (var dep : config.deps()) {
      var depConfig = depConfig(config, dep);
      // TODO[kiva]: should not be null if we have a proper package manager
      if (depConfig == null) continue;
      var depCompiler = DiskLibraryOwner.from(depConfig);
      owner.libraryDepsMut.append(depCompiler);
    }
    return owner;
  }
}
