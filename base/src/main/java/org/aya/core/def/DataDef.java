// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.core.pat.Pat;
import org.aya.core.sort.Sort;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.generic.Level;
import org.aya.generic.Matching;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * core data definition, corresponding to {@link Decl.DataDecl}
 *
 * @author kiva
 */
public final record DataDef(
  @NotNull DefVar<DataDef, Decl.DataDecl> ref,
  @NotNull ImmutableSeq<Term.Param> contextTele,
  @NotNull ImmutableSeq<Term.Param> telescope,
  @NotNull ImmutableSeq<Sort.LvlVar> levels,
  @NotNull Term result,
  @NotNull ImmutableSeq<Ctor> body
) implements Def {
  public DataDef {
    ref.core = this;
  }

  public static @NotNull DefVar<DataDef, Decl.DataDecl> fromCtor(@NotNull DefVar<Ctor, Decl.DataCtor> conHead) {
    if (conHead.core != null) return conHead.core.dataRef();
    else return conHead.concrete.dataRef;
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitData(this, p);
  }

  /**
   * @param ref     in case of GADT constructors, the telescope is not instantiated.
   * @param conTele Needs to be substituted before usage.
   * @param clauses Needs to be substituted before usage.
   * @param result  Needs to be substituted before usage.
   * @author ice1000, kiva
   */
  public static final record Ctor(
    @NotNull DefVar<DataDef, Decl.DataDecl> dataRef,
    @NotNull DefVar<Ctor, Decl.DataCtor> ref,
    @NotNull ImmutableSeq<Pat> pats,
    @NotNull ImmutableSeq<Term.Param> dataTele,
    @NotNull ImmutableSeq<Term.Param> conTele,
    @NotNull ImmutableSeq<Matching<Pat, Term>> clauses,
    @NotNull Term result,
    boolean coerce
  ) implements Def {
    public Ctor {
      ref.core = this;
    }

    @Override public @NotNull ImmutableSeq<Term.Param> contextTele() {
      var ref = dataRef();
      if (ref.core == null) return Objects.requireNonNull(ref.concrete.signature).contextParam();
      return ref.core.contextTele();
    }

    @Override public @NotNull ImmutableSeq<Term.Param> telescope() {
      return dataTele.concat(conTele);
    }

    public static @NotNull ImmutableSeq<Term.Param> conTele(@NotNull DefVar<Ctor, Decl.DataCtor> conVar) {
      if (conVar.core != null) return conVar.core.conTele;
      else return Objects.requireNonNull(conVar.concrete.signature).param();
    }

    /**
     * @return first component: data's telescope, second component: con telescope
     */
    public static @NotNull CtorTelescopes
    telescopes(@NotNull DefVar<Ctor, Decl.DataCtor> defVar, ImmutableSeq<Level<Sort.LvlVar>> sort) {
      var core = defVar.core;
      if (core != null) {
        var dataDef = core.dataRef.core;
        var conTelescope = core.conTele;
        if (dataDef != null)
          return new CtorTelescopes(dataDef.contextTele, dataDef.telescope, sort, conTelescope);
        var signature = core.dataRef.concrete.signature;
        assert signature != null;
        return new CtorTelescopes(signature.contextParam(), signature.param(), sort, conTelescope);
      }
      var dataSignature = defVar.concrete.dataRef.concrete.signature;
      assert dataSignature != null;
      var conSignature = defVar.concrete.signature;
      assert conSignature != null;
      return new CtorTelescopes(dataSignature.contextParam(), dataSignature.param(), sort, conSignature.param());
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitCtor(this, p);
    }
  }

  /**
   * @author ice1000
   */
  public static record CtorTelescopes(
    @NotNull ImmutableSeq<Term.Param> ctxTele,
    @NotNull ImmutableSeq<Term.Param> dataTele,
    @NotNull ImmutableSeq<Level<Sort.LvlVar>> sortTele,
    @NotNull ImmutableSeq<Term.Param> conTele
  ) {
    public @NotNull CallTerm.Con toConCall(DefVar<Ctor, Decl.DataCtor> conVar) {
      return new CallTerm.Con(fromCtor(conVar), conVar,
        ctxTele.map(Term.Param::toArg),
        dataTele.map(Term.Param::toArg),
        sortTele,
        conTele.map(Term.Param::toArg));
    }
  }
}
