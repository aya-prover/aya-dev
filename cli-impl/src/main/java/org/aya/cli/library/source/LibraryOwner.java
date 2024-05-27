// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.source;

import kala.collection.SeqView;
import kala.collection.SetView;
import kala.collection.immutable.ImmutableSet;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableQueue;
import kala.collection.mutable.MutableSet;
import kala.tuple.Tuple;
import org.aya.cli.library.json.LibraryConfig;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.error.SourceFileLocator;
import org.aya.util.terck.MutableGraph;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Common interfaces for library, for supporting both
 * library in file system and library in memory.
 *
 * @author ice1000, kiva
 * @apiNote DO NOT USE this class as Map key, use {@link #underlyingLibrary()} instead.
 * @see DiskLibraryOwner
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

  default @Nullable LibrarySource findModule(@NotNull ModulePath mod) {
    var file = findModuleHere(mod);
    if (file == null) for (var dep : libraryDeps()) {
      file = dep.findModule(mod);
      if (file != null) break;
    }
    return file;
  }

  private @Nullable LibrarySource findModuleHere(@NotNull ModulePath mod) {
    return librarySources().find(s -> s.moduleName().equals(mod)).getOrNull();
  }

  /**
   * Collect all dependencies of {@param owner}
   *
   * @return All dependencies of {@param owner}, including {@param owner}.
   * The result may contain more than one {@link LibraryOwner}s for one {@link LibraryConfig}.
   */
  static @NotNull ImmutableSet<LibraryOwner> collectDependencies(@NotNull LibraryOwner owner) {
    var libs = MutableSet.<LibraryOwner>create();
    var queue = MutableQueue.<LibraryOwner>create();
    queue.enqueue(owner);

    while (queue.isNotEmpty()) {
      var thisOwner = queue.dequeue();
      if (libs.contains(thisOwner)) continue;
      libs.add(thisOwner);
      thisOwner.libraryDeps().forEach(queue::enqueue);
    }

    return libs.toImmutableSet();
  }

  static @NotNull MutableGraph<LibraryConfig> buildDependencyGraph(@NotNull LibraryOwner owner) {
    return buildDependencyGraph(collectDependencies(owner).view());
  }

  static @NotNull MutableGraph<LibraryConfig> buildDependencyGraph(@NotNull SetView<LibraryOwner> owners) {
    var edges = owners.map(owner -> Tuple.of(
      owner.underlyingLibrary(),
      MutableList.from(owner.libraryDeps().map(LibraryOwner::underlyingLibrary)))
    );
    return new MutableGraph<>(MutableMap.from(edges));
  }
}
