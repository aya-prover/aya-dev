// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.SeqView;
import kala.tuple.Tuple;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.ide.action.FindReferences;
import org.aya.lsp.utils.LspRange;
import org.aya.ide.Resolver;
import org.aya.ide.util.XY;
import org.aya.ref.AnyVar;
import org.aya.util.error.WithPos;
import org.javacs.lsp.TextEdit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface Rename {
  static @Nullable WithPos<String> prepare(@NotNull LibrarySource source, XY xy) {
    var vars = Resolver.resolveVar(source, xy);
    if (vars.isEmpty()) return null;
    return vars.first().map(AnyVar::name);
  }

  static Map<URI, List<TextEdit>> rename(
    @NotNull LibrarySource source,
    @NotNull String newName,
    @NotNull SeqView<LibraryOwner> libraries, XY xy
  ) {
    return FindReferences.findOccurrences(source, libraries, xy)
      .flatMap(to -> {
        var edit = new TextEdit(LspRange.toRange(to), newName);
        return to.file().underlying().map(uri -> Tuple.of(uri, edit));
      }).collect(Collectors.groupingBy(tup -> tup._1.toUri(), Collectors.mapping(
        tup -> tup._2,
        Collectors.toList()
      )));
  }
}
