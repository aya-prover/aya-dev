// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.cli.library.source.LibrarySource;
import org.aya.core.def.Def;
import org.aya.core.term.Term;
import org.aya.distill.BaseDistiller;
import org.aya.distill.CoreDistiller;
import org.aya.lsp.utils.Resolver;
import org.aya.pretty.doc.Doc;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;

public interface ComputeSignature {
  static @NotNull Doc invoke(@NotNull LibrarySource loadedFile, @NotNull Position position, boolean withResult) {
    var target = Resolver.resolvePosition(loadedFile, position).firstOrNull();
    if (target == null) return Doc.empty();
    return switch (target.data()) {
      case LocalVar localVar -> BaseDistiller.varDoc(localVar);
      case DefVar<?, ?> defVar -> computeSignature((Def) defVar.core, withResult);
      default -> Doc.empty();
    };
  }

  static @NotNull Doc computeSignature(@NotNull Def core, boolean withResult) {
    var options = DistillerOptions.pretty();
    var distiller = new CoreDistiller(options);
    var tele = distiller.visitTele(core.telescope(), core.result(), Term::findUsages);
    if (withResult) {
      return Doc.stickySep(tele, Doc.symbol(":"), core.result().toDoc(options));
    } else return tele;
  }
}
