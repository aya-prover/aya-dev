// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableArray;
import kala.control.Either;
import kala.control.Option;
import org.aya.core.def.*;
import org.aya.generic.Modifier;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.EnumSet;

/**
 * @author ice1000
 */
public sealed interface SerDef extends Serializable {
  @NotNull Def de(@NotNull SerTerm.DeState state);

  record QName(@NotNull ImmutableArray<String> mod, @NotNull String name, int id) implements Serializable {
  }

  record Fn(
    @NotNull QName name,
    @NotNull ImmutableArray<SerTerm.SerParam> telescope,
    @NotNull ImmutableArray<SerLevel.LvlVar> levels,
    @NotNull Either<SerTerm, ImmutableArray<SerPat.Matchy>> body,
    @NotNull EnumSet<Modifier> modifiers,
    @NotNull SerTerm result
  ) implements SerDef {
    @Override public @NotNull Def de(SerTerm.@NotNull DeState state) {
      return new FnDef(
        state.def(name), telescope.map(tele -> tele.de(state)),
        levels.map(level -> level.de(state.levelCache())),
        result.de(state), modifiers,
        body.map(term -> term.de(state), mischa -> mischa.map(matchy -> matchy.de(state))));
    }
  }

  record Ctor(
    @NotNull QName data, @NotNull QName self,
    @NotNull ImmutableArray<SerPat> pats,
    @NotNull ImmutableArray<SerTerm.SerParam> ownerTele,
    @NotNull ImmutableArray<SerTerm.SerParam> selfTele,
    @NotNull ImmutableArray<SerPat.Matchy> clauses,
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
    @NotNull ImmutableArray<SerTerm.SerParam> telescope,
    @NotNull ImmutableArray<SerLevel.LvlVar> levels,
    @NotNull SerTerm result,
    @NotNull ImmutableArray<Ctor> bodies
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
    @NotNull ImmutableArray<SerTerm.SerParam> ownerTele,
    @NotNull ImmutableArray<SerTerm.SerParam> selfTele,
    @NotNull SerTerm result,
    @NotNull ImmutableArray<SerPat.Matchy> clauses,
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
    @NotNull ImmutableArray<SerTerm.SerParam> telescope,
    @NotNull ImmutableArray<SerLevel.LvlVar> levels,
    @NotNull SerTerm result,
    @NotNull ImmutableArray<Field> fields
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
    @NotNull ImmutableArray<SerTerm.SerParam> telescope,
    @NotNull ImmutableArray<SerLevel.LvlVar> levels,
    @NotNull SerTerm result,
    @NotNull PrimDef.ID name
  ) implements SerDef {
    @Override
    public @NotNull Def de(SerTerm.@NotNull DeState state) {
      return new PrimDef(
        telescope.map(tele -> tele.de(state)),
        levels.map(level -> level.de(state.levelCache())),
        result.de(state),
        name
      );
    }
  }
}
