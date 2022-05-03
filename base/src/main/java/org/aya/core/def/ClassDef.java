// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import org.aya.core.term.Term;
import org.aya.distill.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.ref.ClassDefVar;
import org.aya.ref.DefVar;
import org.aya.util.distill.AyaDocile;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;

public non-sealed/*sealed*/ interface ClassDef extends AyaDocile, GenericDef {
  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return new CoreDistiller(options).def(this);
  }

  @Override @NotNull ClassDefVar<?, ?> ref();

  @NotNull Term result();
}
