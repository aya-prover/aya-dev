// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.core.term.AppTerm;
import org.aya.core.term.Term;
import org.aya.generic.Pat;
import org.glavo.kala.collection.Map;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.SeqView;
import org.glavo.kala.collection.mutable.Buffer;
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
  @NotNull Seq<Term.Param> telescope,
  @NotNull Term result,
  @NotNull Buffer<String> elim,
  @NotNull Buffer<Ctor> ctors,
  @NotNull Map<Pat<Term>, Ctor> clauses // TODO: mix clauses and ctors into one field?
  // TODO: also see RefFinder
) implements Def {
  public DataDef {
    ref.core = this;
  }

  @Override public <P, R> R accept(Visitor<P, R> visitor, P p) {
    return visitor.visitData(this, p);
  }

  public static record Ctor(
    @NotNull DefVar<DataDef, Decl.DataDecl> dataRef,
    @NotNull DefVar<Ctor, Decl.DataCtor> ref,
    @NotNull Seq<Term.Param> conTelescope,
    @NotNull Buffer<String> elim,
    @NotNull Buffer<Pat.Clause<Term>> clauses,
    boolean coerce
  ) implements Def {
    public Ctor {
      ref.core = this;
    }

    @Override public @NotNull SeqView<Term.Param> telescope() {
      return Def.defTele(dataRef).view().map(Term.Param::implicitify).concat(conTelescope);
    }

    @Override public @NotNull Term result() {
      return new AppTerm.DataCall(dataRef, Def.defTele(dataRef).view().map(Term.Param::toArg));
    }

    /**
     * @return first component: data's telescope, second component: con telescope
     */
    public static Tuple2<Seq<Term.Param>, Seq<Term.Param>> telescopes(@NotNull DefVar<Ctor, Decl.DataCtor> defVar) {
      if (defVar.core != null) return Tuple.of(defVar.core.dataRef.core.telescope, defVar.core.conTelescope);
      var dataSignature = defVar.concrete.dataRef.concrete.signature;
      assert dataSignature != null;
      var conSignature = defVar.concrete.signature;
      assert conSignature != null;
      return Tuple.of(dataSignature.param(), conSignature.param());
    }

    @Override public <P, R> R accept(Visitor<P, R> visitor, P p) {
      return null;
    }
  }
}
