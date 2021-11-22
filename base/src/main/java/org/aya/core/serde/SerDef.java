// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.control.Option;
import org.aya.core.def.*;
import org.aya.generic.Modifier;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.OpDecl;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.EnumSet;

/**
 * @author ice1000
 */
public sealed interface SerDef extends Serializable {
  @NotNull Def de(@NotNull SerTerm.DeState state);

  record QName(@NotNull ImmutableSeq<String> mod, @NotNull String name) implements Serializable {
  }

  record Fn(
    @NotNull QName name,
    @NotNull ImmutableSeq<SerTerm.SerParam> telescope,
    @NotNull ImmutableSeq<SerLevel.LvlVar> levels,
    @NotNull Either<SerTerm, ImmutableSeq<SerPat.Matchy>> body,
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
    @NotNull SerLevel.Max result,
    @NotNull ImmutableSeq<Ctor> bodies
  ) implements SerDef {
    @Override public @NotNull Def de(SerTerm.@NotNull DeState state) {
      return new DataDef(
        state.def(name), telescope.map(tele -> tele.de(state)),
        levels.map(level -> level.de(state.levelCache())),
        result.de(state.levelCache()), bodies.map(body -> body.de(state)));
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
    @NotNull SerLevel.Max result,
    @NotNull ImmutableSeq<Field> fields
  ) implements SerDef {
    @Override public @NotNull Def de(SerTerm.@NotNull DeState state) {
      return new StructDef(
        state.def(name),
        telescope.map(tele -> tele.de(state)),
        levels.map(level -> level.de(state.levelCache())),
        result.de(state.levelCache()),
        fields.map(field -> field.de(state))
      );
    }
  }

  record Prim(
    @NotNull PrimDef.ID name
  ) implements SerDef {
    @Override
    public @NotNull Def de(SerTerm.@NotNull DeState state) {
      return PrimDef.Factory.INSTANCE.getOrCreate(name);
    }
  }

  /** To use serialized operators in {@link org.aya.concrete.desugar.AyaBinOpSet} */
  record SerOpDecl(@NotNull OpInfo opInfo) implements OpDecl {
  }

  /** Serialized version of {@link org.aya.util.binop.OpDecl.OpInfo} */
  record SerOp(@NotNull QName name, @NotNull Assoc assoc, int argc, @NotNull SerBind bind) implements Serializable {
  }

  /** Serialized version of {@link org.aya.concrete.stmt.BindBlock} */
  record SerBind(@NotNull ImmutableSeq<QName> loosers, @NotNull ImmutableSeq<QName> tighters) implements Serializable {
    public static final SerBind EMPTY = new SerBind(ImmutableSeq.empty(), ImmutableSeq.empty());
  }
}
