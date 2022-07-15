// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.source;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.library.json.LibraryConfig;
import org.aya.util.error.SourceFileLocator;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Common interfaces for library, for supporting both
 * library in file system and library in memory.
 *
 * @author ice1000, kiva
 * @see DiskLibraryOwner
 * @apiNote DO NOT USE this class as Map key, use {@link #underlyingLibrary()} instead.
 */
@Debug.Renderer(text = "underlyingLibrary().name()")
public interface LibraryOwner {
  int DEFAULT_INDENT = 2;
  /** @return Source dirs of this module, out dirs of all dependencies. */
  @NotNull SeqView<Path> modulePath();
  @NotNull SeqView<LibrarySource> librarySources();
  @NotNull SeqView<LibraryOwner> libraryDeps();
  @NotNull SourceFileLocator locator();
  @NotNull LibraryConfig underlyingLibrary();

  void addModulePath(@NotNull Path newPath);

  /** @return Out dir of this module. */
  default @NotNull Path outDir() {
    return underlyingLibrary().libraryOutRoot();
  }

  default @Nullable LibrarySource findModule(@NotNull ImmutableSeq<String> mod) {
    var file = findModuleHere(mod);
    if (file == null) for (var dep : libraryDeps()) {
      file = dep.findModule(mod);
      if (file != null) break;
    }
    return file;
  }

  private @Nullable LibrarySource findModuleHere(@NotNull ImmutableSeq<String> mod) {
    return librarySources().find(s -> {
      var checkMod = s.moduleName();
      return checkMod.equals(mod);
    }).getOrNull();
  }
}
