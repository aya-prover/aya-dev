// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.cli.library.source.LibrarySource;
import org.aya.lsp.utils.Log;
import org.aya.lsp.utils.LspRange;
import org.aya.lsp.utils.Resolver;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.LocationLink;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author ice1000, kiva
 */
public interface GotoDefinition {
  static @NotNull List<LocationLink> invoke(@NotNull DefinitionParams params, @NotNull LibrarySource loadedFile) {
    return Resolver.resolvePosition(loadedFile, params.getPosition()).mapNotNull(pos -> {
      var from = pos.sourcePos();
      var target = switch (pos.data()) {
        case DefVar<?, ?> defVar -> defVar.concrete.sourcePos();
        case LocalVar localVar -> localVar.definition();
        case default -> null;
      };
      if (target == null) return null;
      var res = LspRange.toLoc(from, target);
      if (res != null) Log.d("Resolved: %s in %s", target, res.getTargetUri());
      return res;
    }).collect(Collectors.toList());
  }
}
