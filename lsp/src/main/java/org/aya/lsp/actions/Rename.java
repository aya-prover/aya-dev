// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.SeqView;
import kala.tuple.Tuple;
import org.aya.api.ref.Var;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.lsp.utils.LspRange;
import org.aya.lsp.utils.Resolver;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface Rename {
  static @Nullable WithPos<String> prepare(@NotNull LibrarySource source, @NotNull Position position) {
    var vars = Resolver.resolveVar(source, position);
    if (vars.isEmpty()) return null;
    return vars.first().map(Var::name);
  }

  static Map<String, List<TextEdit>> rename(
    @NotNull LibrarySource source,
    @NotNull Position position,
    @NotNull String newName,
    @NotNull SeqView<LibraryOwner> libraries
  ) {
    var defs = GotoDefinition.findDefs(source, position, libraries);
    var refs = FindReferences.findRefs(source, position, libraries)
      .map(ref -> new WithPos<>(SourcePos.NONE, ref));

    return defs.concat(refs)
      .flatMap(pos -> {
        var to = pos.data();
        var edit = new TextEdit(LspRange.toRange(to), newName);
        return to.file().underlying().map(uri -> Tuple.of(uri, edit));
      }).collect(Collectors.groupingBy(tup -> tup._1.toUri().toString(), Collectors.mapping(
        tup -> tup._2,
        Collectors.toList()
      )));
  }
}
