// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.term.ConCall;
import org.aya.core.term.SortTerm;
import org.aya.core.term.Term;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
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
    SortTerm result, @NotNull ImmutableSeq<CtorDef> body
  ) {
    super(telescope, result);
    ref.core = this;
    this.ref = ref;
    this.body = body;
  }

  public static @NotNull DefVar<DataDef, TeleDecl.DataDecl> fromCtor(@NotNull DefVar<CtorDef, TeleDecl.DataCtor> conHead) {
    if (conHead.core != null) return conHead.core.dataRef;
    else return conHead.concrete.dataRef;
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
    public CtorTelescopes {
      dataTele = dataTele.map(Term.Param::implicitify);
    }

    public @NotNull ConCall toConCall(DefVar<CtorDef, TeleDecl.DataCtor> conVar, int ulift) {
      return new ConCall(fromCtor(conVar), conVar,
        dataTele.map(Term.Param::toArg),
        ulift,
        conTele.map(Term.Param::toArg));
    }

    public @NotNull CtorTelescopes rename() {
      return new CtorTelescopes(dataTele.map(Term.Param::rename),
        conTele.map(Term.Param::rename));
    }

    public @NotNull ImmutableSeq<Term.Param> params() {
      return dataTele.concat(conTele);
    }
  }
}
