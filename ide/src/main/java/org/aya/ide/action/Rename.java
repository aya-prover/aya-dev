// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import kala.collection.CollectionView;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.ide.Resolver;
import org.aya.ide.util.XY;
import org.aya.syntax.ref.AnyVar;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.NotNull;

public interface Rename {
  static @NotNull Option<WithPos<String>> prepare(@NotNull LibrarySource source, XY xy) {
    var vars = Resolver.resolveVar(source, xy);
    return vars.getFirstOption().map(t -> t.map(AnyVar::name));
  }

  static @NotNull ImmutableSeq<RenameEdit> rename(
    @NotNull LibrarySource source,
    @NotNull String newName,
    @NotNull CollectionView<LibraryOwner> libraries, XY xy
  ) {
    return FindReferences.findOccurrences(source, libraries, xy)
      .map(to -> new RenameEdit(to, newName))
      .toSeq();
  }

  record RenameEdit(
    @NotNull SourcePos sourcePos,
    @NotNull String newText
  ) {}
}
