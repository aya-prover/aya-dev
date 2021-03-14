// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.core.pat.Pat;
import org.aya.core.term.AppTerm;
import org.aya.core.term.Term;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.SeqView;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.control.Option;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.jetbrains.annotations.NotNull;

/**
 * core data definition, corresponding to {@link Decl.DataDecl}
 *
 * @author kiva
 */
public record DataDef(
  @NotNull DefVar<DataDef, Decl.DataDecl> ref,
  @NotNull ImmutableSeq<Term.Param> telescope,
  @NotNull Term result,
  @NotNull ImmutableSeq<Tuple2<Option<Pat>, Ctor>> body
  // TODO: also see RefFinder
) implements Def {
  public DataDef {
    ref.core = this;
  }

  public static @NotNull DefVar<DataDef, Decl.DataDecl> fromCtor(@NotNull DefVar<Ctor, Decl.DataCtor> conHead) {
    if (conHead.core != null) return conHead.core.dataRef();
    else return conHead.concrete.dataRef;
  }

  // TODO: eliminated ctors
  public @NotNull SeqView<@NotNull Ctor> ctors() {
    return body.view()
      .filter(t -> t._1.isEmpty())
      .map(t -> t._2);
  }

  @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitData(this, p);
  }

  public static record Ctor(
    @NotNull DefVar<DataDef, Decl.DataDecl> dataRef,
    @NotNull DefVar<Ctor, Decl.DataCtor> ref,
    @NotNull ImmutableSeq<Term.Param> conTelescope,
    @NotNull ImmutableSeq<Pat.Clause> clauses,
    boolean coerce
  ) implements Def {
    public Ctor {
      ref.core = this;
    }

    @Override public @NotNull SeqView<Term.Param> telescope() {
      return Def.defTele(dataRef).view().map(Term.Param::implicitify).concat(conTelescope);
    }

    @Override public @NotNull AppTerm.DataCall result() {
      return new AppTerm.DataCall(dataRef, Def.defTele(dataRef).view().map(Term.Param::toArg));
    }

    /**
     * @return first component: data's telescope, second component: con telescope
     */
    public static Tuple2<Seq<Term.Param>, Seq<Term.Param>> telescopes(@NotNull DefVar<Ctor, Decl.DataCtor> defVar) {
      var core = defVar.core;
      if (core != null) {
        if (core.dataRef.core != null) return Tuple.of(core.dataRef.core.telescope, core.conTelescope);
        else {
          var signature = core.dataRef.concrete.signature;
          assert signature != null;
          return Tuple.of(signature.param(), core.conTelescope);
        }
      }
      var dataSignature = defVar.concrete.dataRef.concrete.signature;
      assert dataSignature != null;
      var conSignature = defVar.concrete.signature;
      assert conSignature != null;
      return Tuple.of(dataSignature.param(), conSignature.param());
    }

    @Override public <P, R> R accept(@NotNull Visitor<P, R> visitor, P p) {
      return null;
    }

    public Pat.@NotNull Ctor freshPat(boolean explicit) {
      return new Pat.Ctor(explicit, ref,
        conTelescope.map(p -> new Pat.Bind(p.explicit(), p.ref(), p.type())),
        null, result());
    }
  }
}
