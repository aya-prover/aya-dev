// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.library;

import kala.collection.SeqView;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.json.LibraryConfig;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.json.LibraryDependency;
import org.aya.cli.library.source.DiskLibraryOwner;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.cli.library.source.MutableLibraryOwner;
import org.aya.lsp.library.internal.WsLibrary;
import org.aya.syntax.AyaFiles;
import org.aya.util.position.SourceFileLocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;

public interface LibraryOwnerFactory {
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

  default @NotNull LibrarySource source(@NotNull LibraryOwner owner, @NotNull Path file) {
    return LibrarySource.create(owner, file);
  }

  // TODO: Almost identical to DiskLibraryOwner, move to cli to eliminate duplicated code
  default @NotNull MutableLibraryOwner library(@NotNull LibraryConfig config) throws IOException {
    var srcRoot = config.librarySrcRoot();
    var locator = new SourceFileLocator.Module(SeqView.of(srcRoot));
    var owner = new DiskLibraryOwner(locator, MutableList.of(srcRoot), MutableList.create(), MutableList.create(), config);
    owner.librarySourcesMut().appendAll(AyaFiles.collectAyaSourceFiles(srcRoot)
      .map(x -> source(owner, x)));
    for (var dep : config.deps()) {
      var depConfig = depConfig(config, dep);
      // TODO[kiva]: should not be null if we have a proper package manager
      if (depConfig == null) continue;
      var depCompiler = library(depConfig);
      owner.libraryDepsMut().append(depCompiler);
    }
    return owner;
  }

  @NotNull LibraryOwner mock(@NotNull Path source);

  enum Default implements LibraryOwnerFactory {
    INSTANCE;

    @Override
    public @NotNull LibraryOwner mock(@NotNull Path source) {
      return WsLibrary.mock(source);
    }
  }
}
