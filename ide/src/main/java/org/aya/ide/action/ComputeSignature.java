// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import kala.collection.immutable.ImmutableSeq;
import org.aya.cli.library.source.LibrarySource;
import org.aya.concrete.stmt.Decl;
import org.aya.core.def.Def;
import org.aya.core.term.PiTerm;
import org.aya.core.term.Term;
import org.aya.pretty.BasePrettier;
import org.aya.pretty.CorePrettier;
import org.aya.ide.Resolver;
import org.aya.ide.util.XY;
import org.aya.pretty.doc.Doc;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.util.pretty.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public interface ComputeSignature {
  static @NotNull Doc invokeHover(
    @NotNull PrettierOptions options,
    @NotNull LibrarySource source, XY xy
  ) {
    var target = Resolver.resolveVar(source, xy).firstOrNull();
    if (target == null) return Doc.empty();
    return computeSignature(options, target.data(), true);
  }

  @SuppressWarnings("unchecked")
  static @NotNull Doc computeSignature(@NotNull PrettierOptions options, @NotNull AnyVar target, boolean withResult) {
    return switch (target) {
      case LocalVar localVar -> BasePrettier.varDoc(localVar); // TODO: compute type of local vars
      case DefVar<?, ?> ref -> {
        // #299: hovering a mouse on a definition whose header is failed to tyck
        if (!(ref.concrete instanceof Decl.Telescopic<?> concrete)
          || (concrete.signature() == null && ref.core == null)) yield Doc.empty();
        var defVar = (DefVar<? extends Def, ? extends Decl.Telescopic<?>>) ref;
        yield computeSignature(options, Def.defTele(defVar), Def.defResult(defVar), withResult);
      }
      default -> Doc.empty();
    };
  }

  static @NotNull Doc computeSignature(
    @NotNull PrettierOptions options,
    @NotNull ImmutableSeq<Term.Param> defTele, @NotNull Term defResult,
    boolean withResult
  ) {
    if (withResult) {
      var type = PiTerm.make(defTele, defResult);
      return type.toDoc(options);
    }
    var prettier = new CorePrettier(options);
    return prettier.visitTele(defTele, defResult, Term::findUsages);
  }
}
