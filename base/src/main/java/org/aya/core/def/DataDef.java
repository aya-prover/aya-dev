// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.ref.DefVar;
import org.aya.concrete.stmt.Decl;
import org.aya.core.sort.Sort;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

/**
 * core data definition, corresponding to {@link Decl.DataDecl}
 *
 * @author kiva
 */
public final class DataDef extends UserDef {
  public final @NotNull DefVar<DataDef, Decl.DataDecl> ref;
  public final @NotNull ImmutableSeq<CtorDef> body;

  public DataDef(
    @NotNull DefVar<DataDef, Decl.DataDecl> ref, @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull ImmutableSeq<Sort.LvlVar> levels, @NotNull Term result,
    @NotNull ImmutableSeq<CtorDef> body
  ) {
    super(telescope, result, levels);
    ref.core = this;
    this.ref = ref;
    this.body = body;
  }

  public static @NotNull DefVar<DataDef, Decl.DataDecl> fromCtor(@NotNull DefVar<CtorDef, Decl.DataCtor> conHead) {
    if (conHead.core != null) return conHead.core.dataRef;
    else return conHead.concrete.dataRef;
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitData(this, p);
  }

  public @NotNull DefVar<DataDef, Decl.DataDecl> ref() {
    return ref;
  }

  /**
   * @author ice1000
   */
  public record CtorTelescopes(
    @NotNull ImmutableSeq<Term.Param> dataTele,
    @NotNull ImmutableSeq<Sort> sortTele,
    @NotNull ImmutableSeq<Term.Param> conTele
  ) {
    public @NotNull CallTerm.Con toConCall(DefVar<CtorDef, Decl.DataCtor> conVar) {
      return new CallTerm.Con(fromCtor(conVar), conVar,
        dataTele.map(Term.Param::toArg),
        sortTele,
        conTele.map(Term.Param::toArg));
    }

    public @NotNull CtorTelescopes rename() {
      return new CtorTelescopes(dataTele.view()
        .map(Term.Param::implicitify)
        .map(Term.Param::rename)
        .toImmutableSeq(), sortTele, conTele.map(Term.Param::rename));
    }

    public @NotNull ImmutableSeq<Term.Param> params() {
      return dataTele.concat(conTele);
    }
  }
}
