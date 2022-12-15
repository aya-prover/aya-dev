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
import org.aya.generic.SortKind;
import org.aya.guest0x0.cubical.Formula;
import org.aya.guest0x0.cubical.Partial;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * @author ice1000
 */
public sealed interface SerTerm extends Serializable, Restr.TermLike<SerTerm> {
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

    @SuppressWarnings("unchecked") public <V extends DefVar<?, ?>>
    @NotNull V resolve(@NotNull SerDef.QName name) {
      return (V) defCache
        .getOrPut(name.mod(), MutableHashMap::new)
        .getOrPut(name.name(), () -> {
          var empty = DefVar.empty(name.name());
          empty.module = name.mod();
          return empty;
        });
    }

    <V extends DefVar<?, ?>> @NotNull V def(@NotNull SerDef.QName name) {
      return resolve(name);
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
    public @NotNull LocalVar de(@NotNull DeState state) {
      return state.var(this);
    }
  }

  @NotNull Term de(@NotNull DeState state);

  record SerParam(boolean explicit, @NotNull SimpVar var, @NotNull SerTerm term) implements Serializable {
    public @NotNull Term.Param de(@NotNull DeState state) {
      return new Term.Param(var.de(state), term.de(state), explicit);
    }
  }

  record Pi(@NotNull SerTerm.SerParam param, @NotNull SerTerm body) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new PiTerm(param.de(state), body.de(state));
    }
  }

  record Sigma(@NotNull ImmutableSeq<SerParam> params) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new SigmaTerm(params.map(p -> p.de(state)));
    }
  }

  record Sort(@NotNull SortKind kind, int lift) implements SerTerm {
    @Override public @NotNull SortTerm de(@NotNull DeState state) {
      return new SortTerm(kind, lift);
    }
  }

  record Ref(@NotNull SimpVar var) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new RefTerm(var.de(state));
    }
  }

  record Lam(@NotNull SerParam param, @NotNull SerTerm body) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new LamTerm(param.de(state), body.de(state));
    }
  }

  record New(@NotNull SerTerm.Struct call, @NotNull ImmutableMap<SerDef.QName, SerTerm> map) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new NewTerm(call.de(state), ImmutableMap.from(map.view().map((k, v) ->
        Tuple.of(state.resolve(k), v.de(state)))));
    }
  }

  record Proj(@NotNull SerTerm of, int ix) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new ProjTerm(of.de(state), ix);
    }
  }

  record Match(@NotNull ImmutableSeq<SerTerm> of, ImmutableSeq<SerPat.Clause> clauses) implements SerTerm {
    @Override
    public @NotNull Term de(@NotNull DeState state) {
      return new MatchTerm(of.map(t -> t.de(state)), clauses.map(c -> c.de(state)));
    }
  }

  record SerArg(@NotNull SerTerm arg, boolean explicit) implements Serializable {
    public @NotNull Arg<Term> de(@NotNull DeState state) {
      return new Arg<>(arg.de(state), explicit);
    }
  }

  record App(@NotNull SerTerm of, @NotNull SerArg arg) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new AppTerm(of.de(state), arg.de(state));
    }
  }

  record CallData(int ulift, @NotNull ImmutableSeq<SerArg> args) implements Serializable {
    public @NotNull ImmutableSeq<Arg<Term>> de(@NotNull DeState state) {
      return args.map(arg -> arg.de(state));
    }
  }

  record Struct(@NotNull SerDef.QName name, @NotNull CallData data) implements SerTerm {
    @Override public @NotNull StructCall de(@NotNull DeState state) {
      return new StructCall(state.resolve(name), data.ulift, data.de(state));
    }
  }

  record Fn(@NotNull SerDef.QName name, @NotNull CallData data) implements SerTerm {
    @Override public @NotNull FnCall de(@NotNull DeState state) {
      return new FnCall(state.resolve(name), data.ulift, data.de(state));
    }
  }

  record Data(@NotNull SerDef.QName name, @NotNull CallData data) implements SerTerm {
    @Override public @NotNull DataCall de(@NotNull DeState state) {
      return new DataCall(state.resolve(name), data.ulift, data.de(state));
    }
  }

  record Prim(@NotNull SerDef.QName name, @NotNull PrimDef.ID id, @NotNull CallData data) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new PrimCall(state.resolve(name), id, data.ulift, data.de(state));
    }
  }

  record Con(
    @NotNull SerDef.QName dataRef, @NotNull SerDef.QName selfRef,
    @NotNull CallData dataArgs, @NotNull ImmutableSeq<SerArg> conArgs
  ) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new ConCall(
        state.resolve(dataRef), state.resolve(selfRef),
        dataArgs.de(state), dataArgs.ulift,
        conArgs.map(arg -> arg.de(state)));
    }
  }

  record Tup(@NotNull ImmutableSeq<SerArg> components) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new TupTerm(components.map(t -> t.de(state)));
    }
  }

  record Access(
    @NotNull SerTerm of,
    @NotNull SerDef.QName ref,
    @NotNull ImmutableSeq<@NotNull SerArg> structArgs,
    @NotNull ImmutableSeq<@NotNull SerArg> fieldArgs
  ) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new FieldTerm(
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
      return IntervalTerm.INSTANCE;
    }
  }

  record Mula(@NotNull Formula<SerTerm> formula) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new FormulaTerm(formula.fmap(t -> t.de(state)));
    }
  }

  record ShapedInt(
    int integer,
    @NotNull SerDef.SerShapeResult shape,
    @NotNull SerTerm.Data type
  ) implements SerTerm {
    @Override public @NotNull Term de(SerTerm.@NotNull DeState state) {
      return new IntegerTerm(integer, shape.de(state), type.de(state));
    }
  }

  record ShapedList(
    @NotNull ImmutableSeq<SerTerm> repr,
    @NotNull SerDef.SerShapeResult shape,
    @NotNull SerTerm.Data type
  ) implements SerTerm {
    @Override
    public @NotNull Term de(@NotNull DeState state) {
      var termDesered = repr.map(x -> x.de(state));
      var shape = shape().de(state);
      var type = type().de(state);
      return new ListTerm(termDesered, shape, type);
    }
  }

  record Str(@NotNull String string) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new StringTerm(string);
    }
  }

  record PartEl(@NotNull Partial<SerTerm> partial, @NotNull SerTerm rhsType) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new PartialTerm(de(state, partial), rhsType.de(state));
    }

    static @NotNull Partial<Term> de(@NotNull DeState state, @NotNull Partial<SerTerm> par) {
      return par.fmap(t -> t.de(state));
    }
  }

  record PartTy(@NotNull SerTerm type, @NotNull Restr<SerTerm> restr) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new PartialTyTerm(type.de(state), restr.fmap(t -> t.de(state)));
    }
  }

  record Path(
    @NotNull ImmutableSeq<SimpVar> params,
    @NotNull SerTerm type,
    @NotNull Partial<SerTerm> partial
  ) implements SerTerm {
    @Override public @NotNull PathTerm de(@NotNull DeState state) {
      return new PathTerm(
        params.map(p -> p.de(state)),
        type.de(state),
        PartEl.de(state, partial));
    }
  }

  record PathLam(
    @NotNull ImmutableSeq<SimpVar> params,
    @NotNull SerTerm body
  ) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new PLamTerm(params.map(p -> p.de(state)), body.de(state));
    }
  }

  record PathApp(
    @NotNull SerTerm of,
    @NotNull ImmutableSeq<SerArg> args,
    @NotNull Path cube
  ) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new PAppTerm(of.de(state), args.map(arg -> arg.de(state)), cube.de(state));
    }
  }

  record Coe(@NotNull SerTerm type, @NotNull Restr<SerTerm> restr) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new CoeTerm(type.de(state), restr.fmap(t -> t.de(state)));
    }
  }

  record Sub(@NotNull SerTerm type, @NotNull SerTerm restr, @NotNull Partial<SerTerm> partial) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new SubTerm(type.de(state), restr.de(state), partial.fmap(t -> t.de(state)));
    }
  }

  record InOut(@NotNull SerTerm phi, @NotNull SerTerm u, boolean isIntro) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new InOutTerm(phi.de(state), u.de(state), isIntro ? InOutTerm.Kind.In : InOutTerm.Kind.Out);
    }
  }
}
