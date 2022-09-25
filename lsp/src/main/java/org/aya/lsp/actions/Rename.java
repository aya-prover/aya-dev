// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.SeqView;
import kala.tuple.Tuple;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.lsp.utils.LspRange;
import org.aya.lsp.utils.Resolver;
import org.aya.ref.AnyVar;
import org.aya.util.error.WithPos;
import org.javacs.lsp.Position;
import org.javacs.lsp.TextEdit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface Rename {
  static @Nullable WithPos<String> prepare(@NotNull LibrarySource source, @NotNull Position position) {
    var vars = Resolver.resolveVar(source, position);
    if (vars.isEmpty()) return null;
    return vars.first().map(AnyVar::name);
  }

  static Map<URI, List<TextEdit>> rename(
    @NotNull LibrarySource source,
    @NotNull Position position,
    @NotNull String newName,
    @NotNull SeqView<LibraryOwner> libraries
  ) {
    return FindReferences.findOccurrences(source, position, libraries)
      .flatMap(to -> {
        var edit = new TextEdit(LspRange.toRange(to), newName);
        return to.file().underlying().map(uri -> Tuple.of(uri, edit));
      }).collect(Collectors.groupingBy(tup -> tup._1.toUri(), Collectors.mapping(
        tup -> tup._2,
        Collectors.toList()
      )));
  }
}
