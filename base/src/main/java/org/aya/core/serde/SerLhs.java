// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.Matching;
import org.aya.core.pat.Lhs;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * @author tonfeiz
 */
public sealed interface SerLhs extends Serializable {
  @NotNull Lhs de(@NotNull SerTerm.DeState state);

  record Matchy(@NotNull ImmutableSeq<SerLhs> lhss, @NotNull SerTerm body) implements Serializable {
    public @NotNull Matching de(@NotNull SerTerm.DeState state) {
      return new Matching(SourcePos.SER, lhss.map(lhs -> lhs.de(state)), body.de(state));
    }
  }

  record Tuple(boolean explicit, @NotNull ImmutableSeq<SerLhs> lhss) implements SerLhs {
    @Override public @NotNull Lhs de(SerTerm.@NotNull DeState state) {
      return new Lhs.Tuple(explicit, lhss.map(lhs -> lhs.de(state)));
    }
  }

  record Bind(boolean explicit, @NotNull SerTerm.SimpVar var, SerTerm ty) implements SerLhs {
    @Override public @NotNull Lhs de(SerTerm.@NotNull DeState state) {
      return new Lhs.Bind(explicit, state.var(var), ty.de(state));
    }
  }

  record Prim(boolean explicit, @NotNull SerDef.QName name) implements SerLhs {
    @Override public @NotNull Lhs de(SerTerm.@NotNull DeState state) {
      return new Lhs.Prim(explicit, state.resolve(name));
    }
  }

  record Ctor(
    boolean explicit,
    @NotNull SerDef.QName name, @NotNull ImmutableSeq<SerLhs> params,
    @NotNull SerTerm.DataCall ty
  ) implements SerLhs {
    @Override public @NotNull Lhs de(SerTerm.@NotNull DeState state) {
      return new Lhs.Ctor(explicit, state.resolve(name), params.map(param -> param.de(state)), ty.de(state));
    }
  }
}
