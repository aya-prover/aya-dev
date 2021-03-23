// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.core.pat.Pat;
import org.aya.core.term.CallTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.Substituter;
import org.aya.generic.Matching;
import org.glavo.kala.collection.SeqView;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

/**
 * core data definition, corresponding to {@link Decl.DataDecl}
 *
 * @author kiva
 */
public record DataDef(
  @NotNull DefVar<DataDef, Decl.DataDecl> ref,
  @NotNull ImmutableSeq<Term.Param> contextTele,
  @NotNull ImmutableSeq<Term.Param> telescope,
  @NotNull Term result,
  @NotNull ImmutableSeq<Matching<Pat, Ctor>> body
  // TODO: also see RefFinder
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

  public static record CtorInfo(
    @NotNull ImmutableSeq<Term.Param> conTelescope,
    @NotNull ImmutableSeq<Matching<Pat, Term>> clauses
  ) {
    public @NotNull CtorInfo subst(Substituter.TermSubst subst) {
      var conTele = Term.Param.subst(conTelescope, subst);
      return new CtorInfo(conTele, clauses.map(i -> i.mapBody(term -> term.subst(subst))));
    }
  }

  /**
   * @param ref  refers to the original constructor, but in case of GADT constructors, telescope is not instantiated.
   *             If you need to get the available constructors given an instance of {@link CallTerm.Data},
   *             please use {@link CallTerm.Data#availableCtors()}.
   * @param info the information of this ctor. Will be substituted in {@link CallTerm.Data#availableCtors()}.
   *             Use with extreme care!
   * @author ice1000, kiva
   */
  public static record Ctor(
    @NotNull DefVar<DataDef, Decl.DataDecl> dataRef,
    @NotNull DefVar<Ctor, Decl.DataCtor> ref,
    @NotNull CtorInfo info,
    boolean coerce
  ) implements Def {
    public Ctor {
      ref.core = this;
    }

    @Override public @NotNull ImmutableSeq<Term.Param> contextTele() {
      return dataRef().core.contextTele();
    }

    @Override public @NotNull SeqView<Term.Param> telescope() {
      return Def.defTele(dataRef).view().map(Term.Param::implicitify).concat(info.conTelescope);
    }

    @Override public @NotNull CallTerm.Data result() {
      return new CallTerm.Data(
        dataRef,
        Def.defContextTele(dataRef).map(Term.Param::toArg),
        Def.defTele(dataRef).view().map(Term.Param::toArg).toImmutableSeq()
      );
    }

    /**
     * @return first component: data's telescope, second component: con telescope
     */
    public static @NotNull CtorTelescopes telescopes(@NotNull DefVar<Ctor, Decl.DataCtor> defVar) {
      var core = defVar.core;
      if (core != null) {
        var dataDef = core.dataRef.core;
        var conTelescope = core.info.conTelescope;
        if (dataDef != null)
          return new CtorTelescopes(dataDef.contextTele, dataDef.telescope, conTelescope);
        else {
          var signature = core.dataRef.concrete.signature;
          assert signature != null;
          return new CtorTelescopes(signature.contextParam(), signature.param(), conTelescope);
        }
      }
      var dataSignature = defVar.concrete.dataRef.concrete.signature;
      assert dataSignature != null;
      var conSignature = defVar.concrete.signature;
      assert conSignature != null;
      return new CtorTelescopes(dataSignature.contextParam(), dataSignature.param(), conSignature.param());
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return null;
    }
  }

  /**
   * @author ice1000
   */
  public static record CtorTelescopes(
    @NotNull ImmutableSeq<Term.Param> ctxTele,
    @NotNull ImmutableSeq<Term.Param> dataTele,
    @NotNull ImmutableSeq<Term.Param> conTele
  ) {
    public @NotNull CallTerm.Con toConCall(DefVar<Ctor, Decl.DataCtor> conVar) {
      return new CallTerm.Con(fromCtor(conVar), conVar,
        ctxTele.map(Term.Param::toArg),
        dataTele.map(Term.Param::toArg),
        conTele.map(Term.Param::toArg));
    }
  }
}
