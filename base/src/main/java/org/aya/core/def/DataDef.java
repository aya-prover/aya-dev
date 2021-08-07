// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
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
public final class DataDef implements Def {
  public final @NotNull DefVar<DataDef, Decl.DataDecl> ref;
  public final @NotNull ImmutableSeq<Term.Param> telescope;
  public final @NotNull ImmutableSeq<Sort.LvlVar> levels;
  public final @NotNull Term result;
  public final @NotNull ImmutableSeq<CtorDef> body;

  public DataDef(@NotNull DefVar<DataDef, Decl.DataDecl> ref, @NotNull ImmutableSeq<Term.Param> telescope, @NotNull ImmutableSeq<Sort.LvlVar> levels, @NotNull Term result, @NotNull ImmutableSeq<CtorDef> body) {
    ref.core = this;
    this.ref = ref;
    this.telescope = telescope;
    this.levels = levels;
    this.result = result;
    this.body = body;
  }

  public static @NotNull DefVar<DataDef, Decl.DataDecl> fromCtor(@NotNull DefVar<CtorDef, Decl.DataCtor> conHead) {
    if (conHead.core != null) return conHead.core.dataRef();
    else return conHead.concrete.dataRef;
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitData(this, p);
  }

  public @NotNull DefVar<DataDef, Decl.DataDecl> ref() {
    return ref;
  }

  public @NotNull ImmutableSeq<Term.Param> telescope() {
    return telescope;
  }

  public @NotNull ImmutableSeq<Sort.LvlVar> levels() {
    return levels;
  }

  public @NotNull Term result() {
    return result;
  }

  /**
   * @author ice1000
   */
  public static record CtorTelescopes(
    @NotNull ImmutableSeq<Term.Param> dataTele,
    @NotNull ImmutableSeq<Sort.CoreLevel> sortTele,
    @NotNull ImmutableSeq<Term.Param> conTele
  ) {
    public @NotNull CallTerm.Con toConCall(DefVar<CtorDef, Decl.DataCtor> conVar) {
      return new CallTerm.Con(fromCtor(conVar), conVar,
        dataTele.map(Term.Param::toArg),
        sortTele,
        conTele.map(Term.Param::toArg));
    }
  }
}
