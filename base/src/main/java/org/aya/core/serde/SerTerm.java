// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import org.aya.core.def.PrimDef;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.guest0x0.cubical.Formula;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author ice1000
 */
public sealed interface SerTerm extends Serializable {
  record DeState(
    @NotNull MutableMap<Seq<String>, MutableMap<String, DefVar<?, ?>>> defCache,
    @NotNull MutableMap<Integer, LocalVar> localCache,
    @NotNull PrimDef.Factory primFactory
  ) {
    public DeState(@NotNull PrimDef.Factory primFactory) {
      this(MutableMap.create(), MutableMap.create(), primFactory);
    }

    public @NotNull LocalVar var(@NotNull SimpVar var) {
      return localCache.getOrPut(var.var, () -> new LocalVar(var.name));
    }

    @SuppressWarnings("unchecked")
    public <V extends DefVar<?, ?>>
    @NotNull V resolve(@NotNull SerDef.QName name) {
      // We assume this cast to be safe
      var dv = (V) defCache
        .getOrThrow(name.mod(), () -> new SerDef.DeserializeException("Unable to find module: " + name.mod()))
        .getOrThrow(name.name(), () -> new SerDef.DeserializeException("Unable to find DefVar: " + name));
      assert Objects.equals(name.name(), dv.name());
      return dv;
    }

    @SuppressWarnings("unchecked") <V extends DefVar<?, ?>>
    @NotNull V newDef(@NotNull SerDef.QName name) {
      // We assume this cast to be safe
      var defVar = DefVar.empty(name.name());
      var old = defCache
        .getOrPut(name.mod(), MutableHashMap::new)
        .put(name.name(), defVar);
      if (old.isDefined()) throw new SerDef.DeserializeException("Same definition deserialized twice: " + name);
      defVar.module = name.mod();
      return (V) defVar;
    }

    public void putPrim(
      @NotNull ImmutableSeq<String> mod,
      @NotNull PrimDef.ID id,
      @NotNull DefVar<?, ?> defVar
    ) {
      var old = defCache.getOrPut(mod, MutableHashMap::new).put(id.id, defVar);
      if (old.isDefined()) throw new SerDef.DeserializeException("Same prim deserialized twice: " + id.id);
      defVar.module = mod;
    }
  }

  record SimpVar(int var, @NotNull String name) implements Serializable {
  }

  @NotNull Term de(@NotNull DeState state);

  record SerParam(boolean explicit, @NotNull SimpVar var, @NotNull SerTerm term) implements Serializable {
    public @NotNull Term.Param de(@NotNull DeState state) {
      return new Term.Param(state.var(var), term.de(state), explicit);
    }
  }

  record Pi(@NotNull SerTerm.SerParam param, @NotNull SerTerm body) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new FormTerm.Pi(param.de(state), body.de(state));
    }
  }

  record Sigma(@NotNull ImmutableSeq<SerParam> params) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new FormTerm.Sigma(params.map(p -> p.de(state)));
    }
  }

  record Univ(int ulift) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new FormTerm.Univ(ulift);
    }
  }

  record Ref(@NotNull SimpVar var) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new RefTerm(state.var(var));
    }
  }

  record Lam(@NotNull SerParam param, @NotNull SerTerm body) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new IntroTerm.Lambda(param.de(state), body.de(state));
    }
  }

  record New(@NotNull StructCall call, @NotNull ImmutableMap<SerDef.QName, SerTerm> map) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new IntroTerm.New(call.de(state), ImmutableMap.from(map.view().map((k, v) ->
        Tuple.of(state.resolve(k), v.de(state)))));
    }
  }

  record Proj(@NotNull SerTerm of, int ix) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new ElimTerm.Proj(of.de(state), ix);
    }
  }

  record SerArg(@NotNull SerTerm arg, boolean explicit) implements Serializable {
    public @NotNull Arg<Term> de(@NotNull DeState state) {
      return new Arg<>(arg.de(state), explicit);
    }
  }

  record App(@NotNull SerTerm of, @NotNull SerArg arg) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new ElimTerm.App(of.de(state), arg.de(state));
    }
  }

  record CallData(
    int ulift,
    @NotNull ImmutableSeq<SerArg> args
  ) implements Serializable {
    public @NotNull ImmutableSeq<Arg<Term>> de(@NotNull DeState state) {
      return args.map(arg -> arg.de(state));
    }
  }

  record StructCall(@NotNull SerDef.QName name, @NotNull CallData data) implements SerTerm {
    @Override public @NotNull CallTerm.Struct de(@NotNull DeState state) {
      return new CallTerm.Struct(state.resolve(name), data.ulift, data.de(state));
    }
  }

  record FnCall(@NotNull SerDef.QName name, @NotNull CallData data) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new CallTerm.Fn(state.resolve(name), data.ulift, data.de(state));
    }
  }

  record DataCall(@NotNull SerDef.QName name, @NotNull CallData data) implements SerTerm {
    @Override public @NotNull CallTerm.Data de(@NotNull DeState state) {
      return new CallTerm.Data(state.resolve(name), data.ulift, data.de(state));
    }
  }

  record PrimCall(@NotNull SerDef.QName name, @NotNull PrimDef.ID id, @NotNull CallData data) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new CallTerm.Prim(state.resolve(name), id, data.ulift, data.de(state));
    }
  }

  record ConCall(
    @NotNull SerDef.QName dataRef, @NotNull SerDef.QName selfRef,
    @NotNull CallData dataArgs, @NotNull ImmutableSeq<SerArg> args
  ) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new CallTerm.Con(
        state.resolve(dataRef), state.resolve(selfRef),
        dataArgs.de(state), dataArgs.ulift,
        args.map(arg -> arg.de(state)));
    }
  }

  record Tup(@NotNull ImmutableSeq<SerTerm> components) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new IntroTerm.Tuple(components.map(t -> t.de(state)));
    }
  }

  record Access(
    @NotNull SerTerm of,
    @NotNull SerDef.QName ref,
    @NotNull ImmutableSeq<@NotNull SerArg> structArgs,
    @NotNull ImmutableSeq<@NotNull SerArg> fieldArgs
  ) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new CallTerm.Access(
        of.de(state), state.resolve(ref),
        structArgs.map(arg -> arg.de(state)),
        fieldArgs.map(arg -> arg.de(state)));
    }
  }

  record FieldRef(@NotNull SerDef.QName name) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new RefTerm.Field(state.resolve(name));
    }
  }

  record Interval() implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return FormTerm.Interval.INSTANCE;
    }
  }

  record Mula(@NotNull SerMula formula) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return formula.de(state);
    }
  }

  sealed interface SerMula extends Serializable {
    default @NotNull Term de(@NotNull DeState state) {
      return switch (this) {
        case Conn cnn -> new PrimTerm.Mula(new Formula.Conn<>(
          cnn.isAnd(), cnn.l().de(state), cnn.r().de(state)));
        case Inv inv -> new PrimTerm.Mula(new Formula.Inv<>(inv.i().de(state)));
        case Lit lit -> lit.isLeft() ? PrimTerm.Mula.LEFT : PrimTerm.Mula.RIGHT;
      };
    }
    record Conn(boolean isAnd, @NotNull SerTerm l, @NotNull SerTerm r) implements SerMula {}
    record Inv(@NotNull SerTerm i) implements SerMula {}
    record Lit(boolean isLeft) implements SerMula {}
  }

  record ShapedInt(
    int integer,
    @NotNull SerDef.SerAyaShape shape,
    @NotNull SerTerm type
  ) implements SerTerm {
    @Override public @NotNull Term de(SerTerm.@NotNull DeState state) {
      return new LitTerm.ShapedInt(integer, shape.de(), type.de(state));
    }
  }

  record Str(@NotNull String string) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new PrimTerm.Str(string);
    }
  }
}
