// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.source;

import kala.collection.SeqView;
import kala.collection.mutable.DynamicSeq;
import org.aya.api.error.CountingReporter;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.cli.library.json.LibraryConfig;
import org.aya.cli.library.json.LibraryConfigData;
import org.aya.cli.library.json.LibraryDependency;
import org.aya.generic.Constants;
import org.aya.util.FileUtil;
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
  @NotNull CountingReporter reporter,
  @NotNull SourceFileLocator locator,
  @NotNull DynamicSeq<Path> thisModulePath,
  @NotNull DynamicSeq<LibraryOwner> dependencies,
  @NotNull DynamicSeq<LibrarySource> sources,
  @NotNull LibraryConfig underlyingLibrary
) implements LibraryOwner {
  private static @Nullable LibraryConfig depConfig(@NotNull LibraryConfig config, @NotNull LibraryDependency dep) throws IOException {
    // TODO: test only: dependency resolving should be done in package manager
    if (dep instanceof LibraryDependency.DepFile file)
      return LibraryConfigData.fromDependencyRoot(file.depRoot(), version -> depBuildRoot(config, dep.depName(), version));
    return null;
  }

  private static @NotNull Path depBuildRoot(@NotNull LibraryConfig config, @NotNull String depName, @NotNull String version) {
    return config.libraryBuildRoot().resolve("deps").resolve(depName + "_" + version);
  }

  public static @NotNull DiskLibraryOwner from(@NotNull Reporter outReporter, @NotNull LibraryConfig config) throws IOException {
    var reporter = CountingReporter.of(outReporter);
    var srcRoot = config.librarySrcRoot();
    var locator = new SourceFileLocator.Module(SeqView.of(srcRoot));
    var owner = new DiskLibraryOwner(reporter, locator, DynamicSeq.of(),
      DynamicSeq.of(), DynamicSeq.of(), config);
    owner.sources.appendAll(FileUtil.collectSource(srcRoot, Constants.AYA_POSTFIX)
      .map(p -> new LibrarySource(owner, p)));
    for (var dep : config.deps()) {
      var depConfig = depConfig(config, dep);
      // TODO[kiva]: should not be null if we have a proper package manager
      if (depConfig == null) {
        reporter.reportString("Skipping " + dep.depName());
        continue;
      }
      var depCompiler = DiskLibraryOwner.from(reporter, depConfig);
      owner.dependencies.append(depCompiler);
    }
    return owner;
  }

  @Override public @NotNull SeqView<Path> modulePath() {
    return thisModulePath.view();
  }

  @Override public @NotNull SeqView<LibrarySource> librarySourceFiles() {
    return sources.view();
  }

  @Override public @NotNull SeqView<LibraryOwner> libraryDeps() {
    return dependencies.view();
  }

  @Override public void registerModulePath(@NotNull Path newPath) {
    thisModulePath.append(newPath);
  }
}
