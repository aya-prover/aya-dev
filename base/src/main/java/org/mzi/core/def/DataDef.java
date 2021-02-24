// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.core.def;

import org.glavo.kala.collection.immutable.ImmutableMap;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.jetbrains.annotations.NotNull;
import org.mzi.api.ref.DefVar;
import org.mzi.concrete.Decl;
import org.mzi.core.term.Term;
import org.mzi.generic.Pat;

/**
 * core data definition, corresponding to {@link org.mzi.concrete.Decl.DataDecl}
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
    @NotNull DefVar<Ctor, Decl.DataCtor> name,
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull Buffer<String> elim,
    @NotNull Buffer<Pat.Clause<Term>> clauses,
    boolean coerce
  ) {
  }
}
