// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.library;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import org.aya.api.error.CountingReporter;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourceFileLocator;
import org.aya.cli.library.json.LibraryConfig;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.generic.Constants;
import org.aya.prelude.GeneratedVersion;
import org.aya.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Each workspace folder passed by frontend will be mapped to a WsLibrary.
 *
 * @author kiva
 */
public record WsLibrary(
  @NotNull CountingReporter reporter,
  @NotNull DynamicSeq<LibrarySource> sources,
  @NotNull LibraryConfig mockConfig,
  @NotNull Path workspace
) implements LibraryOwner, SourceFileLocator {
  public static @NotNull WsLibrary from(@NotNull Reporter outReporter, @NotNull Path folder) {
    var reporter = CountingReporter.of(outReporter);
    var mockConfig = makeConfig(folder);
    var owner = new WsLibrary(reporter, DynamicSeq.create(), mockConfig, folder);
    owner.sources.appendAll(FileUtil.collectSource(folder, Constants.AYA_POSTFIX, 1)
      .map(path -> new LibrarySource(owner, folder)));
    return owner;
  }

  private static @NotNull LibraryConfig makeConfig(@NotNull Path folder) {
    return new LibraryConfig(
      GeneratedVersion.VERSION,
      folder.getFileName().toString(),
      "1.0.0",
      folder,
      folder,
      folder.resolve("build"),
      folder.resolve("build"),
      ImmutableSeq.empty()
    );
  }

  @Override public @NotNull Path displayName(@NotNull Path path) {
    return path.toAbsolutePath();
  }

  @Override public @NotNull SeqView<Path> modulePath() {
    return SeqView.of(workspace);
  }

  @Override public @NotNull SeqView<LibrarySource> librarySourceFiles() {
    return sources.view();
  }

  @Override public @NotNull SeqView<LibraryOwner> libraryDeps() {
    return SeqView.empty();
  }

  @Override public @NotNull SourceFileLocator locator() {
    return this;
  }

  @Override public @NotNull LibraryConfig underlyingLibrary() {
    return mockConfig;
  }

  @Override public void registerModulePath(@NotNull Path newPath) {
    // do nothing
  }
}
