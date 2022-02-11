// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.concrete.stmt.Decl;
import org.aya.core.Matching;
import org.aya.core.term.Term;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

public final class FieldDef extends SubLevelDef {
  public final @NotNull DefVar<StructDef, Decl.StructDecl> structRef;
  public final @NotNull DefVar<FieldDef, Decl.StructField> ref;
  public final @NotNull Option<Term> body;

  public FieldDef(
    @NotNull DefVar<StructDef, Decl.StructDecl> structRef, @NotNull DefVar<FieldDef, Decl.StructField> ref,
    @NotNull ImmutableSeq<Term.Param> ownerTele, @NotNull ImmutableSeq<Term.Param> selfTele,
    @NotNull Term result, @NotNull ImmutableSeq<Matching> clauses, @NotNull Option<Term> body, boolean coerce
  ) {
    super(ownerTele, selfTele, result, clauses, coerce);
    ref.core = this;
    this.structRef = structRef;
    this.ref = ref;
    this.body = body;
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitField(this, p);
  }

  @Override public @NotNull ImmutableSeq<Term.Param> telescope() {
    return selfTele;
  }

  public @NotNull DefVar<FieldDef, Decl.StructField> ref() {
    return ref;
  }
}
