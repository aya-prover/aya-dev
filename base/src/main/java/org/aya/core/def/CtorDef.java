// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.ref.DefVar;
import org.aya.concrete.stmt.Decl;
import org.aya.core.Matching;
import org.aya.core.pat.Pat;
import org.aya.core.sort.Sort;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author ice1000, kiva
 */
public final class CtorDef extends SubLevelDef {
  public final @NotNull DefVar<DataDef, Decl.DataDecl> dataRef;
  public final @NotNull DefVar<CtorDef, Decl.DataCtor> ref;
  public final @NotNull ImmutableSeq<Pat> pats;

  public CtorDef(
    @NotNull DefVar<DataDef, Decl.DataDecl> dataRef, @NotNull DefVar<CtorDef, Decl.DataCtor> ref,
    @NotNull ImmutableSeq<Pat> pats,
    @NotNull ImmutableSeq<Term.Param> ownerTele, @NotNull ImmutableSeq<Term.Param> selfTele,
    @NotNull ImmutableSeq<Matching> clauses, @NotNull Term result, boolean coerce
  ) {
    super(ownerTele, selfTele, result, clauses, coerce);
    ref.core = this;
    this.dataRef = dataRef;
    this.ref = ref;
    this.pats = pats;
  }

  public static @NotNull ImmutableSeq<Term.Param> conTele(@NotNull DefVar<CtorDef, Decl.DataCtor> conVar) {
    if (conVar.core != null) return conVar.core.selfTele;
    else return Objects.requireNonNull(conVar.concrete.signature).param();
  }

  /**
   * @return first component: data's telescope, second component: con telescope
   */
  public static @NotNull DataDef.CtorTelescopes
  telescopes(@NotNull DefVar<CtorDef, Decl.DataCtor> defVar, ImmutableSeq<Sort> sort) {
    var core = defVar.core;
    if (core != null) return new DataDef.CtorTelescopes(core.ownerTele, sort, core.selfTele);
    var dataSignature = defVar.concrete.patternTele;
    assert dataSignature != null;
    var conSignature = defVar.concrete.signature;
    assert conSignature != null;
    return new DataDef.CtorTelescopes(dataSignature, sort, conSignature.param());
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitCtor(this, p);
  }

  public @NotNull DefVar<CtorDef, Decl.DataCtor> ref() {
    return ref;
  }

  @Override public @NotNull ImmutableSeq<Term.Param> telescope() {
    return fullTelescope().toImmutableSeq();
  }
}
