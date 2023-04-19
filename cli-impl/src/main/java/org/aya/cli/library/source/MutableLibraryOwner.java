// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library.source;

import kala.collection.SeqView;
import kala.collection.mutable.MutableList;
import org.aya.util.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public interface MutableLibraryOwner extends LibraryOwner {
  @NotNull MutableList<Path> modulePathMut();
  @NotNull MutableList<LibrarySource> librarySourcesMut();
  @NotNull MutableList<LibraryOwner> libraryDepsMut();

  @Override default @NotNull SeqView<Path> modulePath() {
    return modulePathMut().view();
  }

  @Override default @NotNull SeqView<LibrarySource> librarySources() {
    return librarySourcesMut().view();
  }

  @Override default @NotNull SeqView<LibraryOwner> libraryDeps() {
    return libraryDepsMut().view();
  }

  @Override default void addModulePath(@NotNull Path newPath) {
    modulePathMut().append(newPath);
  }

  default void removeLibrarySource(@NotNull LibrarySource source) {
    this.librarySourcesMut().removeIf(src -> src == source);
  }

  default @NotNull LibrarySource addLibrarySource(@NotNull Path source) {
    var src = LibrarySource.create(this, FileUtil.canonicalize(source));
    this.librarySourcesMut().append(src);
    return src;
  }
}
