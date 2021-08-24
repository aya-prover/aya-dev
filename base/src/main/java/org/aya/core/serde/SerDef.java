// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.control.Option;
import org.aya.core.def.*;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * @author ice1000
 */
public sealed interface SerDef extends Serializable {
  @NotNull Def de(@NotNull SerTerm.DeState state);

  record QName(@NotNull ImmutableSeq<String> mod, @NotNull String name, int id) implements Serializable {
  }

  record Fn(
    @NotNull QName name,
    @NotNull ImmutableSeq<SerTerm.SerParam> telescope,
    @NotNull ImmutableSeq<SerLevel.LvlVar> levels,
    @NotNull Either<SerTerm, ImmutableSeq<SerPat.Matchy>> body,
    @NotNull SerTerm result
  ) implements SerDef {
    @Override public @NotNull Def de(SerTerm.@NotNull DeState state) {
      return new FnDef(
        state.def(name), telescope.map(tele -> tele.de(state)),
        levels.map(level -> level.de(state.levelCache())),
        result.de(state),
        body.map(term -> term.de(state), mischa -> mischa.map(matchy -> matchy.de(state))));
    }
  }

  record Ctor(
    @NotNull QName data, @NotNull QName self,
    @NotNull ImmutableSeq<SerPat> pats,
    @NotNull ImmutableSeq<SerTerm.SerParam> ownerTele,
    @NotNull ImmutableSeq<SerTerm.SerParam> selfTele,
    @NotNull ImmutableSeq<SerPat.Matchy> clauses,
    @NotNull SerTerm result, boolean coerce
  ) implements SerDef {
    @Override public @NotNull CtorDef de(SerTerm.@NotNull DeState state) {
      return new CtorDef(
        state.def(data), state.def(self), pats.map(pat -> pat.de(state)),
        ownerTele.map(tele -> tele.de(state)), selfTele.map(tele -> tele.de(state)),
        clauses.map(matching -> matching.de(state)),
        result.de(state), coerce);
    }
  }

  record Data(
    @NotNull QName name,
    @NotNull ImmutableSeq<SerTerm.SerParam> telescope,
    @NotNull ImmutableSeq<SerLevel.LvlVar> levels,
    @NotNull SerTerm result,
    @NotNull ImmutableSeq<Ctor> bodies
  ) implements SerDef {
    @Override public @NotNull Def de(SerTerm.@NotNull DeState state) {
      return new DataDef(
        state.def(name), telescope.map(tele -> tele.de(state)),
        levels.map(level -> level.de(state.levelCache())),
        result.de(state),
        bodies.map(body -> body.de(state)));
    }
  }

  record Field(
    @NotNull QName struct,
    @NotNull QName self,
    @NotNull ImmutableSeq<SerTerm.SerParam> ownerTele,
    @NotNull ImmutableSeq<SerTerm.SerParam> selfTele,
    @NotNull SerTerm result,
    @NotNull ImmutableSeq<SerPat.Matchy> clauses,
    @NotNull Option<SerTerm> body,
    boolean coerce
    ) implements SerDef {
    @Override
    public @NotNull FieldDef de(SerTerm.@NotNull DeState state) {
      return new FieldDef(
        state.def(struct),
        state.def(self),
        ownerTele.map(tele -> tele.de(state)),
        selfTele.map(tele -> tele.de(state)),
        result.de(state),
        clauses.map(matching -> matching.de(state)),
        body.map(serTerm -> serTerm.de(state)),
        coerce
      );
    }
  }

  record Struct(
    @NotNull QName name,
    @NotNull ImmutableSeq<SerTerm.SerParam> telescope,
    @NotNull ImmutableSeq<SerLevel.LvlVar> levels,
    @NotNull SerTerm result,
    @NotNull ImmutableSeq<Field> fields
  ) implements SerDef {
    @Override public @NotNull Def de(SerTerm.@NotNull DeState state) {
      return new StructDef(
        state.def(name),
        telescope.map(tele -> tele.de(state)),
        levels.map(level -> level.de(state.levelCache())),
        result.de(state),
        fields.map(field -> field.de(state))
      );
    }
  }

  record Prim(
    @NotNull ImmutableSeq<SerTerm.SerParam> telescope,
    @NotNull ImmutableSeq<SerLevel.LvlVar> levels,
    @NotNull SerTerm result,
    @NotNull String name
  ) implements SerDef {
    @Override
    public @NotNull Def de(SerTerm.@NotNull DeState state) {
      return new PrimDef(
        telescope.map(tele -> tele.de(state)),
        levels.map(level -> level.de(state.levelCache())),
        result.de(state),
        PrimDef.PrimFactory.UNFOLD.get(name),
        name
      );
    }
  }
}
