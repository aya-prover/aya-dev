// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import org.aya.concrete.stmt.decl.ClassDecl;
import org.aya.generic.AyaDocile;
import org.aya.prettier.CorePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public non-sealed/*sealed*/ interface ClassDef extends AyaDocile, GenericDef {
  @Override default @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new CorePrettier(options).def(this);
  }

  @Override @NotNull DefVar<? extends ClassDef, ? extends ClassDecl> ref();
}
