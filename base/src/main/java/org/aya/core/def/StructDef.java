// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import org.aya.api.ref.DefVar;
import org.aya.concrete.stmt.Decl;
import org.aya.core.sort.Sort;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * core struct definition, corresponding to {@link Decl.StructDecl}
 *
 * @author vont
 */

public final class StructDef extends UserDef {
  public final @NotNull DefVar<StructDef, Decl.StructDecl> ref;
  public final @NotNull ImmutableArray<FieldDef> fields;

  public StructDef(
    @NotNull DefVar<StructDef, Decl.StructDecl> ref,
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull ImmutableSeq<Sort.LvlVar> levels,
    @NotNull Term result,
    @NotNull ImmutableSeq<FieldDef> fields
  ) {
    super(telescope, result, levels);
    ref.core = this;
    this.ref = ref;
    this.fields = fields.toImmutableArray();
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitStruct(this, p);
  }

  public @NotNull DefVar<StructDef, Decl.StructDecl> ref() {
    return ref;
  }
}
