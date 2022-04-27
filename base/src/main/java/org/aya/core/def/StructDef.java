// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.TopTeleDecl;
import org.aya.concrete.stmt.StructDecl;
import org.aya.core.term.FormTerm;
import org.aya.core.term.Term;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * core struct definition, corresponding to {@link StructDecl}
 *
 * @author vont
 */

public final class StructDef extends ClassDef {
  public final @NotNull DefVar<StructDef, StructDecl> ref;
  public final @NotNull ImmutableSeq<FieldDef> fields;

  public StructDef(
    @NotNull DefVar<StructDef, StructDecl> ref,
    int ulift,
    @NotNull ImmutableSeq<FieldDef> fields
  ) {
    super();
    ref.core = this;
    this.ref = ref;
    this.fields = fields;
    this.resultLevel = ulift;
  }

  @Override public @NotNull DefVar<StructDef, StructDecl> ref() {
    return ref;
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitStruct(this, p);
  }

  public @NotNull DefVar<StructDef, StructDecl> ref() {
    return ref;
  }

  @Override public @NotNull Term result() {
    return new FormTerm.Univ(resultLevel);
  }
}
