// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.def;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.core.term.AppTerm;
import org.aya.core.term.Term;
import org.aya.generic.Pat;
import org.glavo.kala.collection.immutable.ImmutableMap;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
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
  @NotNull Buffer<String> elim,
  @NotNull Buffer<Ctor> ctors,
  @NotNull ImmutableMap<Pat<Term>, Ctor> clauses // TODO: mix clauses and ctors into one field?
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
    @NotNull DefVar<Ctor, Decl.DataCtor> name,
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull Buffer<String> elim,
    @NotNull Buffer<Pat.Clause<Term>> clauses,
    boolean coerce
  ) implements Def {
    public Ctor {
      name.core = this;
    }

    @Override public @NotNull Term result() {
      return new AppTerm.DataCall(dataRef, Def.defTele(dataRef).map(Term.Param::toArg));
    }

    @Override public @NotNull DefVar<Ctor, Decl.DataCtor> ref() {
      return name;
    }

    @Override public <P, R> R accept(Visitor<P, R> visitor, P p) {
      return null;
    }
  }
}
