// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.library;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.json.LibraryConfig;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.prelude.GeneratedVersion;
import org.aya.util.FileUtil;
import org.aya.util.error.SourceFileLocator;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Each workspace folder passed by frontend will be mapped to a WsLibrary.
 *
 * @author kiva
 */
public record WsLibrary(
  @NotNull SourceFileLocator locator,
  @NotNull MutableList<LibrarySource> sources,
  @NotNull LibraryConfig mockConfig,
  @NotNull Path workspace
) implements LibraryOwner {
  public static @NotNull WsLibrary mock(@NotNull Path ayaSource) {
    var canonicalPath = FileUtil.canonicalize(ayaSource);
    var parent = canonicalPath.getParent();
    var mockConfig = mockConfig(parent);
    var locator = new SourceFileLocator.Module(SeqView.of(parent));
    var owner = new WsLibrary(locator, MutableList.create(), mockConfig, parent);
    owner.sources.append(LibrarySource.create(owner, canonicalPath));
    return owner;
  }

  private static @NotNull LibraryConfig mockConfig(@NotNull Path folder) {
    return new LibraryConfig(
      GeneratedVersion.VERSION,
      folder.getFileName().toString(),
      "1.0.0",
      folder,
      folder,
      folder.resolve("build"),
      folder.resolve("build"),
      new LibraryConfig.LibraryLiterateConfig(null, "/", folder.resolve("build")),
      ImmutableSeq.empty()
    );
  }
  @Override public @NotNull SeqView<Path> modulePath() { return SeqView.of(workspace); }
  @Override public @NotNull SeqView<LibrarySource> librarySources() { return sources.view(); }
  @Override public @NotNull SeqView<LibraryOwner> libraryDeps() { return SeqView.empty(); }
  @Override public @NotNull LibraryConfig underlyingLibrary() { return mockConfig; }
  @Override public void addModulePath(@NotNull Path newPath) {
    // do nothing
  }
}
