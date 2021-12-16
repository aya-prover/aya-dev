// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import org.aya.api.distill.DistillerOptions;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.ref.Var;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.Expr;
import org.aya.core.def.Def;
import org.aya.core.term.Term;
import org.aya.distill.BaseDistiller;
import org.aya.distill.CoreDistiller;
import org.aya.lsp.utils.Log;
import org.aya.lsp.utils.Resolver;
import org.aya.pretty.doc.Doc;
import org.eclipse.lsp4j.Position;
import org.jetbrains.annotations.NotNull;

public interface ComputeSignature {
  static @NotNull Doc invokeHover(
    @NotNull LibrarySource loadedFile,
    @NotNull Position position
  ) {
    var target = Resolver.resolveVar(loadedFile, position).firstOrNull();
    if (target == null) return Doc.empty();
    return computeSignature(target.data(), true);
  }

  static @NotNull Doc invokeSignatureHelp(
    @NotNull LibrarySource loadedFile,
    @NotNull Position position
  ) {
    var head = Resolver.resolveAppHead(loadedFile, position);
    Log.d("Resolved app head: %s", head.map(e -> e.toDoc(DistillerOptions.pretty()).debugRender()));
    if (head.isEmpty()) return Doc.empty();
    if (head.get() instanceof Expr.RefExpr ref)
      return computeSignature(ref.resolvedVar(), false);
    return Doc.empty();
  }

  static @NotNull Doc computeSignature(@NotNull Var target, boolean withResult) {
    return switch (target) {
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
      var res = core.result().toDoc(options);
      if (tele.isEmpty()) return res;
      return Doc.stickySep(tele, Doc.symbol(":"), res);
    } else return tele;
  }
}
