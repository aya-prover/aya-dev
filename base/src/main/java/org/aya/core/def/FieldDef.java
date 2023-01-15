// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.term.SortTerm;
import org.aya.core.term.Term;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class FieldDef extends SubLevelDef {
  public final @NotNull DefVar<StructDef, TeleDecl.StructDecl> structRef;
  public final @NotNull DefVar<FieldDef, TeleDecl.StructField> ref;
  public final @NotNull Option<Term> body;

  public FieldDef(
    @NotNull DefVar<StructDef, TeleDecl.StructDecl> structRef, @NotNull DefVar<FieldDef, TeleDecl.StructField> ref,
    @NotNull ImmutableSeq<Term.Param> ownerTele, @NotNull ImmutableSeq<Term.Param> selfTele,
    @NotNull Term result, @NotNull Option<Term> body, boolean coerce
  ) {
    super(ownerTele, selfTele, result, coerce);
    ref.core = this;
    this.structRef = structRef;
    this.ref = ref;
    this.body = body;
  }

  @Override public @NotNull ImmutableSeq<Term.Param> telescope() {
    return selfTele;
  }

  public @NotNull DefVar<FieldDef, TeleDecl.StructField> ref() {
    return ref;
  }

  private @NotNull SortTerm structResult() {
    return structRef.concrete == null ? structRef.core.result
      : Objects.requireNonNull(structRef.concrete.signature).result();
  }

  public boolean inProp() {
    return structResult().isProp();
  }
}
