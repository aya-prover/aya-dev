// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.ClassDecl;
import org.aya.core.term.FormTerm;
import org.aya.core.term.Term;
import org.aya.distill.CoreDistiller;
import org.aya.generic.AyaDocile;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.NotNull;

public sealed interface ClassDef extends AyaDocile, GenericDef {
  @Override default @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return new CoreDistiller(options).def(this);
  }

  @Override @NotNull DefVar<? extends ClassDef, ? extends ClassDecl> ref();

  abstract sealed class Type implements ClassDef permits StructDef {
    public final int resultLevel;

    protected Type(int resultLevel) {
      super();
      this.resultLevel = resultLevel;
    }
    @Override public @NotNull Term result() {
      return new FormTerm.Univ(resultLevel);
    }
  }
}
