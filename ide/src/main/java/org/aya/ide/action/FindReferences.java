// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import kala.collection.SeqView;
import kala.collection.mutable.MutableList;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.ide.Resolver;
import org.aya.ide.util.XY;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.DefVar;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;

public interface FindReferences {
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
      var resolver = new Resolver.UsageResolver(var, MutableList.create());
      return libraries.flatMap(lib -> resolve(resolver, lib));
    });
  }

  static @NotNull SeqView<SourcePos> findRefsOutsideDefs(
    @NotNull LibrarySource source,
    @NotNull SeqView<LibraryOwner> libraries, XY xy
  ) {
    var vars = Resolver.resolveVar(source, xy);
    return findRefsOutsideDefs(vars.map(WithPos::data), libraries);
  }

  static @NotNull SeqView<SourcePos> findRefsOutsideDefs(
    @NotNull SeqView<AnyVar> vars,
    @NotNull SeqView<LibraryOwner> libraries
  ) {
    var defPos = vars.filterIsInstance(DefVar.class).map(def -> def.concrete.entireSourcePos());
    return findRefs(vars, libraries).filter(ref -> defPos.noneMatch(pos -> pos.containsIndex(ref)));
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
    owner.librarySources().map(src -> src.program().get()).filterNotNull()
      .forEach(prog -> prog.forEach(resolver));
    return resolver.collect().view().concat(owner.libraryDeps().flatMap(dep -> resolve(resolver, dep)));
  }
}
