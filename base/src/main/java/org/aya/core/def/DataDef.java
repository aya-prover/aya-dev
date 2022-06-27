// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

/**
 * core data definition, corresponding to {@link TeleDecl.DataDecl}
 *
 * @author kiva
 */
public final class DataDef extends UserDef.Type {
  public final @NotNull DefVar<DataDef, TeleDecl.DataDecl> ref;
  public final @NotNull ImmutableSeq<CtorDef> body;

  public DataDef(
    @NotNull DefVar<DataDef, TeleDecl.DataDecl> ref, @NotNull ImmutableSeq<Term.Param> telescope,
    int ulift, @NotNull ImmutableSeq<CtorDef> body
  ) {
    super(telescope, ulift);
    ref.core = this;
    this.ref = ref;
    this.body = body;
  }

  public static @NotNull DefVar<DataDef, TeleDecl.DataDecl> fromCtor(@NotNull DefVar<CtorDef, TeleDecl.DataCtor> conHead) {
    if (conHead.core != null) return conHead.core.dataRef;
    else return conHead.concrete.dataRef;
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitData(this, p);
  }

  public @NotNull DefVar<DataDef, TeleDecl.DataDecl> ref() {
    return ref;
  }

  /**
   * @author ice1000
   */
  public record CtorTelescopes(
    @NotNull ImmutableSeq<Term.Param> dataTele,
    @NotNull ImmutableSeq<Term.Param> conTele
  ) {
    public @NotNull CallTerm.Con toConCall(DefVar<CtorDef, TeleDecl.DataCtor> conVar) {
      return new CallTerm.Con(fromCtor(conVar), conVar,
        dataTele.map(Term.Param::toArg),
        0, // TODO: is this correct?
        conTele.map(Term.Param::toArg));
    }

    public @NotNull CtorTelescopes rename() {
      return new CtorTelescopes(dataTele.view()
        .map(Term.Param::implicitify)
        .map(Term.Param::rename)
        .toImmutableSeq(), conTele.map(Term.Param::rename));
    }

    public @NotNull ImmutableSeq<Term.Param> params() {
      return dataTele.concat(conTele);
    }
  }
}
