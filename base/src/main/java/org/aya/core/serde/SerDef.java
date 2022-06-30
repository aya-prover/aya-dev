// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.control.Option;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.def.*;
import org.aya.core.repr.AyaShape;
import org.aya.generic.Constants;
import org.aya.generic.Modifier;
import org.aya.generic.util.InternalException;
import org.aya.ref.DefVar;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.OpDecl;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.EnumSet;

/**
 * @author ice1000
 */
public sealed interface SerDef extends Serializable {
  @NotNull GenericDef de(@NotNull SerTerm.DeState state);

  record QName(@NotNull ImmutableSeq<String> mod, @NotNull String name) implements Serializable {
    @Override public String toString() {
      return mod.joinToString(Constants.SCOPE_SEPARATOR, "", Constants.SCOPE_SEPARATOR + name);
    }
  }

  record Fn(
    @NotNull QName name,
    @NotNull ImmutableSeq<SerTerm.SerParam> telescope,
    @NotNull Either<SerTerm, ImmutableSeq<SerPat.Matchy>> body,
    @NotNull EnumSet<Modifier> modifiers,
    @NotNull SerTerm result
  ) implements SerDef {
    @Override public @NotNull Def de(SerTerm.@NotNull DeState state) {
      return new FnDef(
        state.newDef(name), telescope.map(tele -> tele.de(state)),
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
        state.resolve(data), state.newDef(self), pats.map(pat -> pat.de(state)),
        ownerTele.map(tele -> tele.de(state)), selfTele.map(tele -> tele.de(state)),
        clauses.map(matching -> matching.de(state)),
        result.de(state), coerce);
    }
  }

  record Data(
    @NotNull QName name,
    @NotNull ImmutableSeq<SerTerm.SerParam> telescope,
    int resultLift,
    @NotNull ImmutableSeq<Ctor> bodies
  ) implements SerDef {
    @Override public @NotNull Def de(SerTerm.@NotNull DeState state) {
      return new DataDef(
        state.newDef(name), telescope.map(tele -> tele.de(state)),
        resultLift, bodies.map(body -> body.de(state)));
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
        state.resolve(struct),
        state.newDef(self),
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
    int resultLift,
    @NotNull ImmutableSeq<Field> fields
  ) implements SerDef {
    @Override public @NotNull StructDef de(SerTerm.@NotNull DeState state) {
      return new StructDef(
        state.newDef(name),
        resultLift,
        fields.map(field -> field.de(state))
      );
    }
  }

  record Prim(
    @NotNull ImmutableSeq<String> module,
    @NotNull PrimDef.ID name
  ) implements SerDef {
    @Override
    public @NotNull Def de(SerTerm.@NotNull DeState state) {
      var defVar = DefVar.<PrimDef, TeleDecl.PrimDecl>empty(name.id);
      var def = state.primFactory().getOrCreate(name, defVar);
      state.putPrim(module, name, def.ref);
      return def;
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

  /** serialized {@link AyaShape} */
  enum SerAyaShape implements Serializable {
    NAT;

    public @NotNull AyaShape de() {
      return switch (this) {
        case NAT -> AyaShape.NAT_SHAPE;
      };
    }

    public static @NotNull SerAyaShape serialize(@NotNull AyaShape shape) {
      if (shape == AyaShape.NAT_SHAPE) return NAT;
      throw new InternalException("unexpected shape: " + shape.getClass());
    }
  }

  class DeserializeException extends InternalException {
    public DeserializeException(@NotNull String reason) {
      super(reason);
    }

    @Override public void printHint() {
      System.out.println(getMessage());
    }

    @Override public int exitCode() {
      return 99;
    }
  }
}
