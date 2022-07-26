// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.SeqView;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.lsp.utils.LspRange;
import org.aya.lsp.utils.Resolver;
import org.aya.ref.Var;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public interface FindReferences {
  static @NotNull List<Location> invoke(
    @NotNull LibrarySource source,
    @NotNull Position position,
    @NotNull SeqView<LibraryOwner> libraries
  ) {
    return findRefs(source, position, libraries)
      .map(LspRange::toLoc)
      .collect(Collectors.toList());
  }

  static @NotNull SeqView<SourcePos> findRefs(
    @NotNull LibrarySource source,
    @NotNull Position position,
    @NotNull SeqView<LibraryOwner> libraries
  ) {
    var vars = Resolver.resolveVar(source, position);
    return findRefs(vars.map(WithPos::data), libraries);
  }

  static @NotNull SeqView<SourcePos> findRefs(
    @NotNull SeqView<Var> vars,
    @NotNull SeqView<LibraryOwner> libraries
  ) {
    var resolver = new Resolver.UsageResolver();
    vars.forEach(def -> libraries.forEach(lib -> resolve(resolver, lib, def)));
    return resolver.refs.view();
  }

  static @NotNull SeqView<SourcePos> findOccurrences(
    @NotNull LibrarySource source,
    @NotNull Position position,
    @NotNull SeqView<LibraryOwner> libraries
  ) {
    var defs = GotoDefinition.findDefs(source, position, libraries).map(WithPos::data);
    var refs = FindReferences.findRefs(source, position, libraries);
    return defs.concat(refs);
  }

  private static void resolve(@NotNull Resolver.UsageResolver resolver, @NotNull LibraryOwner owner, @NotNull Var var) {
    owner.librarySources().forEach(src -> {
      var program = src.program().get();
      if (program != null) resolver.visitAll(program, var);
    });
    owner.libraryDeps().forEach(dep -> resolve(resolver, dep, var));
  }
}
