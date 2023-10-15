// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import kala.tuple.Tuple;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.*;
import org.aya.core.repr.AyaShape;
import org.aya.core.repr.CodeShape;
import org.aya.core.repr.ShapeRecognition;
import org.aya.generic.Constants;
import org.aya.generic.Modifier;
import org.aya.guest0x0.cubical.Partial;
import org.aya.ref.DefVar;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.OpDecl;
import org.aya.util.error.InternalException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.EnumSet;

/**
 * @author ice1000
 */
public sealed interface SerDef extends Serializable {
  @NotNull GenericDef de(@NotNull SerTerm.DeState state);

  record QName(@NotNull ImmutableSeq<String> mod, @NotNull ImmutableSeq<String> fileMod,
               @NotNull String name) implements Serializable {
    @Override public String toString() {
      return mod.joinToString(Constants.SCOPE_SEPARATOR, "", Constants.SCOPE_SEPARATOR + name);
    }
  }

  record Fn(
    @NotNull QName name,
    @NotNull ImmutableSeq<SerTerm.SerParam> telescope,
    @NotNull Either<SerTerm, ImmutableSeq<SerPat.Clause>> body,
    @NotNull EnumSet<Modifier> modifiers,
    @NotNull SerTerm result,
    @Override @Nullable SerShapeResult shapeResult
  ) implements SerDef, SerShapable {
    @Override public @NotNull Def de(SerTerm.@NotNull DeState state) {
      return new FnDef(
        state.def(name), telescope.map(tele -> tele.de(state)),
        result.de(state), modifiers,
        body.map(term -> term.de(state), mischa -> mischa.map(clause -> clause.de(state))));
    }
  }

  record Ctor(
    @NotNull QName data, @NotNull QName self,
    @NotNull ImmutableSeq<SerPat> pats,
    @NotNull ImmutableSeq<SerTerm.SerParam> ownerTele,
    @NotNull ImmutableSeq<SerTerm.SerParam> selfTele,
    @NotNull Partial.Split<SerTerm> clauses,
    @NotNull SerTerm result, boolean coerce
  ) implements SerDef {
    @Override public @NotNull CtorDef de(SerTerm.@NotNull DeState state) {
      return new CtorDef(
        state.resolve(data), state.def(self), pats.map(pat -> pat.de(state)),
        ownerTele.map(tele -> tele.de(state)), selfTele.map(tele -> tele.de(state)),
        clauses.fmap(t -> t.de(state)),
        result.de(state), coerce);
    }
  }

  record Data(
    @NotNull QName name,
    @NotNull ImmutableSeq<SerTerm.SerParam> telescope,
    @NotNull SerTerm.Sort resultLift,
    @NotNull ImmutableSeq<Ctor> bodies
  ) implements SerDef {
    @Override public @NotNull Def de(SerTerm.@NotNull DeState state) {
      return new DataDef(
        state.def(name), telescope.map(tele -> tele.de(state)),
        resultLift.de(state), bodies.map(body -> body.de(state)));
    }
  }

  record Field(
    @NotNull QName struct,
    @NotNull QName self,
    @NotNull ImmutableSeq<SerTerm.SerParam> telescope,
    @NotNull SerTerm result,
    boolean coerce
  ) implements SerDef {
    @Override
    public @NotNull MemberDef de(SerTerm.@NotNull DeState state) {
      return new MemberDef(
        state.resolve(struct),
        state.def(self),
        telescope.map(tele -> tele.de(state)),
        result.de(state),
        coerce);
    }
  }

  record Clazz(
    @NotNull QName name,
    @NotNull ImmutableSeq<Field> fields
  ) implements SerDef {
    @Override public @NotNull ClassDef de(SerTerm.@NotNull DeState state) {
      return new ClassDef(state.def(name), fields.map(field -> field.de(state)));
    }
  }

  record Prim(
    @NotNull ImmutableSeq<String> module,
    @NotNull ImmutableSeq<String> fileModule,
    @NotNull PrimDef.ID name
  ) implements SerDef {
    @Override
    public @NotNull Def de(SerTerm.@NotNull DeState state) {
      var defVar = DefVar.<PrimDef, TeleDecl.PrimDecl>empty(name.id);
      var def = state.primFactory().getOrCreate(name, defVar);
      state.putPrim(module, fileModule, name, def.ref);
      return def;
    }
  }

  /** To use serialized operators in {@link org.aya.concrete.desugar.AyaBinOpSet} */
  record SerOpDecl(@NotNull OpInfo opInfo) implements OpDecl, Serializable {
  }

  /** Serialized version of {@link org.aya.util.binop.OpDecl.OpInfo} */
  record SerOp(@NotNull QName name, @NotNull Assoc assoc, @NotNull SerBind bind) implements Serializable {
  }

  /** Serialized version of {@link org.aya.resolve.ResolveInfo.RenamedOpDecl} */
  record SerRenamedOp(@NotNull String name, @NotNull Assoc assoc, @NotNull SerBind bind) implements Serializable {
  }

  /** Serialized version of {@link org.aya.concrete.stmt.BindBlock} */
  record SerBind(@NotNull ImmutableSeq<QName> loosers, @NotNull ImmutableSeq<QName> tighters) implements Serializable {
    public static final SerBind EMPTY = new SerBind(ImmutableSeq.empty(), ImmutableSeq.empty());
  }

  /** serialized {@link ShapeRecognition} */
  record SerShapeResult(
    @NotNull SerAyaShape shape,
    @NotNull ImmutableMap<CodeShape.GlobalId, QName> captures
  ) implements Serializable {
    public @NotNull ShapeRecognition de(@NotNull SerTerm.DeState state) {
      return new ShapeRecognition(shape.de(), ImmutableMap.from(captures.view()
        .map((m, q) -> Tuple.of(m, state.resolve(q)))));
    }

    public static @NotNull SerShapeResult serialize(@NotNull Serializer.State state, @NotNull ShapeRecognition result) {
      return new SerShapeResult(SerAyaShape.serialize(result.shape()), ImmutableMap.from(result.captures().view()
        .map((m, q) -> Tuple.of(m, state.def(q)))));
    }
  }

  /** serialized {@link AyaShape} */
  enum SerAyaShape implements Serializable {
    NAT, LIST;

    public @NotNull AyaShape de() {
      return switch (this) {
        case NAT -> AyaShape.NAT_SHAPE;
        case LIST -> AyaShape.LIST_SHAPE;
      };
    }

    public static @NotNull SerAyaShape serialize(@NotNull AyaShape shape) {
      if (shape == AyaShape.NAT_SHAPE) return NAT;
      if (shape == AyaShape.LIST_SHAPE) return LIST;
      throw new InternalException("unexpected shape: " + shape.getClass());
    }
  }

  class DeserializeException extends InternalException {
    public DeserializeException(@NotNull String reason) {
      super(reason);
    }

    @Override public int exitCode() {
      return 99;
    }
  }
}
