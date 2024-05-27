// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ide.action;

import org.aya.cli.library.source.LibrarySource;
import org.aya.ide.Resolver;
import org.aya.ide.util.XY;
import org.aya.prettier.BasePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.def.TyckAnyDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public interface ComputeSignature {
  static @NotNull Doc invokeHover(
    @NotNull PrettierOptions options,
    @NotNull LibrarySource source, XY xy
  ) {
    var target = Resolver.resolveVar(source, xy).getFirstOrNull();
    if (target == null) return Doc.empty();
    return computeSignature(options, target.data());
  }

  static @NotNull Doc computeSignature(@NotNull PrettierOptions options, @NotNull AnyVar target) {
    return switch (target) {
      case LocalVar localVar -> BasePrettier.varDoc(localVar); // TODO: compute type of local vars
      case DefVar<?, ?> ref -> {
        // #299: hovering a mouse on a definition whose header is failed to tyck
        if (ref.signature == null && ref.core == null) yield Doc.empty();
        yield TyckDef.defType(new TyckAnyDef<>(ref)).toDoc(options);
      }
      default -> Doc.empty();
    };
  }
}
