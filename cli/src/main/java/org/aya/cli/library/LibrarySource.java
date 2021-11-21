// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.library;

import kala.collection.mutable.DynamicSeq;
import org.aya.cli.library.json.LibraryConfig;
import org.aya.concrete.resolve.ResolveInfo;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Objects;

public record LibrarySource(
  @NotNull LibraryConfig owner,
  @NotNull Path file,
  @NotNull DynamicSeq<LibrarySource> imports
) {
  public LibrarySource(@NotNull LibraryConfig owner, @NotNull Path file) {
    this(owner, ResolveInfo.canonicalize(file), DynamicSeq.create());
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LibrarySource that = (LibrarySource) o;
    return owner == that.owner && file.equals(that.file);
  }

  @Override public int hashCode() {
    return Objects.hash(owner, file);
  }
}
