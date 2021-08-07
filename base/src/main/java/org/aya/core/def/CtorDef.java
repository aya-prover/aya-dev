// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
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
public final class CtorDef implements Def {
  public final @NotNull DefVar<DataDef, Decl.DataDecl> dataRef;
  public final @NotNull DefVar<CtorDef, Decl.DataCtor> ref;
  public final @NotNull ImmutableSeq<Pat> pats;
  public final @NotNull ImmutableSeq<Term.Param> dataTele;
  public final @NotNull ImmutableSeq<Term.Param> conTele;
  public final @NotNull ImmutableSeq<Matching> clauses;
  public final @NotNull Term result;
  public final boolean coerce;

  public CtorDef(@NotNull DefVar<DataDef, Decl.DataDecl> dataRef, @NotNull DefVar<CtorDef, Decl.DataCtor> ref, @NotNull ImmutableSeq<Pat> pats, @NotNull ImmutableSeq<Term.Param> dataTele, @NotNull ImmutableSeq<Term.Param> conTele, @NotNull ImmutableSeq<Matching> clauses, @NotNull Term result, boolean coerce) {
    ref.core = this;
    this.dataRef = dataRef;
    this.ref = ref;
    this.pats = pats;
    this.dataTele = dataTele;
    this.conTele = conTele;
    this.clauses = clauses;
    this.result = result;
    this.coerce = coerce;
  }

  @Override public @NotNull ImmutableSeq<Term.Param> telescope() {
    return dataTele.concat(conTele);
  }

  public static @NotNull ImmutableSeq<Term.Param> conTele(@NotNull DefVar<CtorDef, Decl.DataCtor> conVar) {
    if (conVar.core != null) return conVar.core.conTele;
    else return Objects.requireNonNull(conVar.concrete.signature).param();
  }

  /**
   * @return first component: data's telescope, second component: con telescope
   */
  public static @NotNull DataDef.CtorTelescopes
  telescopes(@NotNull DefVar<CtorDef, Decl.DataCtor> defVar, ImmutableSeq<Sort.CoreLevel> sort) {
    var core = defVar.core;
    if (core != null) {
      var dataDef = core.dataRef.core;
      var conTelescope = core.conTele;
      if (dataDef != null)
        return new DataDef.CtorTelescopes(dataDef.telescope, sort, conTelescope);
      var signature = core.dataRef.concrete.signature;
      assert signature != null;
      return new DataDef.CtorTelescopes(signature.param(), sort, conTelescope);
    }
    var dataSignature = defVar.concrete.dataRef.concrete.signature;
    assert dataSignature != null;
    var conSignature = defVar.concrete.signature;
    assert conSignature != null;
    return new DataDef.CtorTelescopes(dataSignature.param(), sort, conSignature.param());
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitCtor(this, p);
  }

  public @NotNull DefVar<DataDef, Decl.DataDecl> dataRef() {
    return dataRef;
  }

  public @NotNull DefVar<CtorDef, Decl.DataCtor> ref() {
    return ref;
  }

  public @NotNull ImmutableSeq<Pat> pats() {
    return pats;
  }

  public @NotNull ImmutableSeq<Term.Param> dataTele() {
    return dataTele;
  }

  public @NotNull ImmutableSeq<Term.Param> conTele() {
    return conTele;
  }

  public @NotNull ImmutableSeq<Matching> clauses() {
    return clauses;
  }

  public @NotNull Term result() {
    return result;
  }
}
