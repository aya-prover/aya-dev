// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import kala.collection.SeqView;
import org.aya.cli.library.source.LibraryOwner;
import org.aya.cli.library.source.LibrarySource;
import org.aya.ide.Resolver;
import org.aya.ide.util.ModuleVar;
import org.aya.ide.util.XY;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.GeneralizedVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000, kiva
 */
public interface GotoDefinition {
  static @NotNull SeqView<WithPos<SourcePos>> findDefs(
    @NotNull LibrarySource source,
    @NotNull SeqView<LibraryOwner> libraries, XY xy
  ) {
    return Resolver.resolveVar(source, xy).mapNotNull(pos -> {
      var from = pos.sourcePos();
      var target = switch (pos.data()) {
        case DefVar<?, ?> defVar -> defVar.concrete.sourcePos();
        case LocalVar localVar -> localVar.definition();
        case ModuleVar moduleVar -> mockSourcePos(libraries, moduleVar);
        case GeneralizedVar gVar -> gVar.sourcePos;
        default -> null;
      };
      if (target == null) return null;
      return new WithPos<>(from, target);
    });
  }

  private static @Nullable SourcePos mockSourcePos(@NotNull SeqView<LibraryOwner> libraries, @NotNull ModuleVar moduleVar) {
    return Resolver.resolveModule(libraries, new ModulePath(moduleVar.path().ids()))
      .map(src -> src.originalFile(""))
      .map(src -> new SourcePos(src, 0, 0, 1, 0, 1, 0))
      .getOrNull();
  }
}
