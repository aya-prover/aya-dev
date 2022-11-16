// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableSeq;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * @author ice1000
 */
public sealed interface SerPat extends Serializable {
  @NotNull Pat de(@NotNull SerTerm.DeState state);

  record Clause(@NotNull ImmutableSeq<SerPat> pats, @NotNull SerTerm body) implements Serializable {
    public @NotNull Term.Matching de(@NotNull SerTerm.DeState state) {
      return new Term.Matching(SourcePos.SER, pats.map(pat -> pat.de(state)), body.de(state));
    }
  }

  record Absurd(boolean explicit) implements SerPat {
    @Override public @NotNull Pat de(SerTerm.@NotNull DeState state) {
      return new Pat.Absurd(explicit);
    }
  }

  record Tuple(boolean explicit, @NotNull ImmutableSeq<SerPat> pats) implements SerPat {
    @Override public @NotNull Pat de(SerTerm.@NotNull DeState state) {
      return new Pat.Tuple(explicit, pats.map(pat -> pat.de(state)));
    }
  }

  record Bind(boolean explicit, @NotNull SerTerm.SimpVar var, @NotNull SerTerm ty) implements SerPat {
    @Override public @NotNull Pat de(SerTerm.@NotNull DeState state) {
      return new Pat.Bind(explicit, var.de(state), ty.de(state));
    }
  }

  record Ctor(
    boolean explicit,
    @NotNull SerDef.QName name,
    @NotNull ImmutableSeq<SerPat> params,
    @NotNull SerTerm.Data ty
  ) implements SerPat {
    @Override public @NotNull Pat de(SerTerm.@NotNull DeState state) {
      return new Pat.Ctor(explicit, state.resolve(name),
        params.map(param -> param.de(state)),
        ty.de(state));
    }
  }

  record End(
    boolean isOne,
    boolean explicit
  ) implements SerPat {
    @Override
    public @NotNull Pat de(SerTerm.@NotNull DeState state) {
      return new Pat.End(isOne, explicit);
    }
  }

  record ShapedInt(
    int integer,
    boolean explicit,
    @NotNull SerDef.SerShapeResult shape,
    @NotNull SerTerm.Data type
  ) implements SerPat {
    @Override public @NotNull Pat de(SerTerm.@NotNull DeState state) {
      return new Pat.ShapedInt(integer, shape.de(state), type.de(state), explicit);
    }
  }
}
