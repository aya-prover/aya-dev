// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.lsp.actions;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.stmt.Decl;
import org.aya.core.def.Def;
import org.aya.core.term.PiTerm;
import org.aya.core.term.Term;
import org.aya.distill.BaseDistiller;
import org.aya.distill.CoreDistiller;
import org.aya.ide.Resolver;
import org.aya.ide.util.XY;
import org.aya.pretty.doc.Doc;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;

public interface ComputeSignature {
  static @NotNull Doc invokeHover(
    @NotNull LibrarySource source, XY xy
  ) {
    var target = Resolver.resolveVar(source, xy).firstOrNull();
    if (target == null) return Doc.empty();
    return computeSignature(target.data(), true);
  }

  @SuppressWarnings("unchecked")
  static @NotNull Doc computeSignature(@NotNull AnyVar target, boolean withResult) {
    return switch (target) {
      case LocalVar localVar -> BaseDistiller.varDoc(localVar); // TODO: compute type of local vars
      case DefVar<?, ?> ref -> {
        // #299: hovering a mouse on a definition whose header is failed to tyck
        if (!(ref.concrete instanceof Decl.Telescopic<?> concrete) || concrete.signature() == null) yield Doc.empty();
        var defVar = (DefVar<? extends Def, ? extends Decl.Telescopic<?>>) ref;
        yield computeSignature(Def.defTele(defVar), Def.defResult(defVar), withResult);
      }
      default -> Doc.empty();
    };
  }

  static @NotNull Doc computeSignature(@NotNull ImmutableSeq<Term.Param> defTele, @NotNull Term defResult, boolean withResult) {
    var options = DistillerOptions.pretty();
    if (withResult) {
      var type = PiTerm.make(defTele, defResult);
      return type.toDoc(options);
    }
    var distiller = new CoreDistiller(options);
    return distiller.visitTele(defTele, defResult, Term::findUsages);
  }
}
