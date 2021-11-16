// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableArray;
import org.aya.core.Matching;
import org.aya.core.pat.Pat;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * @author ice1000
 */
public sealed interface SerPat extends Serializable {
  @NotNull Pat de(@NotNull SerTerm.DeState state);

  record Matchy(@NotNull ImmutableArray<SerPat> pats, @NotNull SerTerm body) implements Serializable {
    public @NotNull Matching de(@NotNull SerTerm.DeState state) {
      return new Matching(SourcePos.NONE, pats.map(pat -> pat.de(state)), body.de(state));
    }
  }

  record Absurd(boolean explicit, @NotNull SerTerm ty) implements SerPat {
    @Override public @NotNull Pat de(SerTerm.@NotNull DeState state) {
      return new Pat.Absurd(explicit, ty.de(state));
    }
  }

  record Tuple(boolean explicit, @NotNull ImmutableArray<SerPat> pats, @NotNull SerTerm.SimpVar as,
               @NotNull SerTerm ty) implements SerPat {
    @Override public @NotNull Pat de(SerTerm.@NotNull DeState state) {
      return new Pat.Tuple(explicit, pats.map(pat -> pat.de(state)), as.var() < 0 ? null : state.var(as), ty.de(state));
    }
  }

  record Bind(boolean explicit, @NotNull SerTerm.SimpVar var, @NotNull SerTerm ty) implements SerPat {
    @Override public @NotNull Pat de(SerTerm.@NotNull DeState state) {
      return new Pat.Bind(explicit, state.var(var), ty.de(state));
    }
  }

  record Prim(boolean explicit, @NotNull SerDef.QName name, @NotNull SerTerm ty) implements SerPat {
    @Override public @NotNull Pat de(SerTerm.@NotNull DeState state) {
      return new Pat.Prim(explicit, state.def(name), ty.de(state));
    }
  }

  record Ctor(
    boolean explicit,
    @NotNull SerDef.QName name, @NotNull ImmutableArray<SerPat> params, @NotNull SerTerm.SimpVar as,
    @NotNull SerTerm.DataCall ty
  ) implements SerPat {
    @Override public @NotNull Pat de(SerTerm.@NotNull DeState state) {
      return new Pat.Ctor(
        explicit, state.def(name), params.map(param -> param.de(state)),
        as.var() < 0 ? null : state.var(as), ty.de(state));
    }
  }
}
