// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.api.ref.DefVar;
import org.aya.concrete.stmt.Decl;
import org.aya.core.Matching;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

public final class FieldDef implements Def {
  public final @NotNull DefVar<StructDef, Decl.StructDecl> structRef;
  public final @NotNull DefVar<FieldDef, Decl.StructField> ref;
  public final @NotNull ImmutableSeq<Term.Param> structTele;
  public final @NotNull ImmutableSeq<Term.Param> fieldTele;
  public final @NotNull Term result;
  public final @NotNull ImmutableSeq<Matching> clauses;
  public final @NotNull Option<Term> body;
  public final boolean coerce;

  public FieldDef(@NotNull DefVar<StructDef, Decl.StructDecl> structRef, @NotNull DefVar<FieldDef, Decl.StructField> ref, @NotNull ImmutableSeq<Term.Param> structTele, @NotNull ImmutableSeq<Term.Param> fieldTele, @NotNull Term result, @NotNull ImmutableSeq<Matching> clauses, @NotNull Option<Term> body, boolean coerce) {
    ref.core = this;
    this.structRef = structRef;
    this.ref = ref;
    this.structTele = structTele;
    this.fieldTele = fieldTele;
    this.result = result;
    this.clauses = clauses;
    this.body = body;
    this.coerce = coerce;
  }

  @Override public @NotNull ImmutableSeq<Term.Param> telescope() {
    return structTele.concat(fieldTele);
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitField(this, p);
  }

  public @NotNull DefVar<FieldDef, Decl.StructField> ref() {
    return ref;
  }

  public @NotNull Term result() {
    return result;
  }
}
