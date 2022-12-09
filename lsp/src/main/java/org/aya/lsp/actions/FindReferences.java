// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.SeqView;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.ide.Resolver;
import org.aya.lsp.utils.LspRange;
import org.aya.ide.util.XY;
import org.aya.ref.AnyVar;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.javacs.lsp.Location;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public interface FindReferences {
  static @NotNull List<Location> invoke(
    @NotNull LibrarySource source,
    @NotNull SeqView<LibraryOwner> libraries, XY xy
  ) {
    return findRefs(source, libraries, xy)
      .map(LspRange::toLoc)
      .collect(Collectors.toList());
  }

  static @NotNull SeqView<SourcePos> findRefs(
    @NotNull LibrarySource source,
    @NotNull SeqView<LibraryOwner> libraries, XY xy
  ) {
    var vars = Resolver.resolveVar(source, xy);
    return findRefs(vars.map(WithPos::data), libraries);
  }

  static @NotNull SeqView<SourcePos> findRefs(
    @NotNull SeqView<AnyVar> vars,
    @NotNull SeqView<LibraryOwner> libraries
  ) {
    return vars.flatMap(var -> {
      var resolver = new Resolver.UsageResolver(var);
      return libraries.flatMap(lib -> resolve(resolver, lib));
    });
  }

  static @NotNull SeqView<SourcePos> findOccurrences(
    @NotNull LibrarySource source,
    @NotNull SeqView<LibraryOwner> libraries, XY xy
  ) {
    var defs = GotoDefinition.findDefs(source, libraries, xy).map(WithPos::data);
    var refs = FindReferences.findRefs(source, libraries, xy);
    return defs.concat(refs);
  }

  private static @NotNull SeqView<SourcePos> resolve(@NotNull Resolver.UsageResolver resolver, @NotNull LibraryOwner owner) {
    return owner.librarySources().map(src -> src.program().get()).filterNotNull()
      .flatMap(prog -> prog.view().flatMap(resolver))
      .concat(owner.libraryDeps().flatMap(dep -> resolve(resolver, dep)));
  }
}
