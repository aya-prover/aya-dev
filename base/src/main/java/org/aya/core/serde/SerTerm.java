// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import org.aya.api.concrete.ConcreteDecl;
import org.aya.api.core.CoreDef;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.core.def.PrimDef;
import org.aya.concrete.resolve.context.Context;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author ice1000
 */
public sealed interface SerTerm extends Serializable {
  record DeState(
    @NotNull Context context,
    @NotNull MutableMap<Seq<String>, MutableMap<String, DefVar<?, ?>>> defCache,
    @NotNull MutableMap<Integer, Sort.LvlVar> levelCache,
    @NotNull MutableMap<Integer, LocalVar> localCache
  ) {
    public DeState(@NotNull Context context) {
      this(context, MutableMap.create(), MutableMap.create(), MutableMap.create());
    }

    public @NotNull LocalVar var(@NotNull SimpVar var) {
      return localCache.getOrPut(var.var, () -> new LocalVar(var.name));
    }

    @SuppressWarnings("unchecked")
    public <Core extends CoreDef, Concrete extends ConcreteDecl>
    @NotNull DefVar<Core, Concrete> def(@NotNull SerDef.QName name) {
      // Resolving this module's definition, it's safe to create new
      if (context.holds(name.mod())) {
        // We assume this cast to be safe
        var dv = (DefVar<Core, Concrete>) defCache
          .getOrPut(name.mod(), MutableHashMap::new)
          .getOrPut(name.name(), () -> DefVar.empty(name.name()));
        assert Objects.equals(name.name(), dv.name());
        dv.module = name.mod();
        return dv;
      } else {
        // Resolving imported definition, it's only safe to resolve from context.
        return (DefVar<Core, Concrete>) context.getQualified(
          name.mod(),
          name.name(),
          SourcePos.SER
        );
      }
    }
  }

  record SimpVar(int var, @NotNull String name) implements Serializable {
  }

  @NotNull Term de(@NotNull DeState state);

  record SerParam(boolean explicit, boolean pattern, @NotNull SimpVar var,
                  @NotNull SerTerm term) implements Serializable {
    public @NotNull Term.Param de(@NotNull DeState state) {
      return new Term.Param(state.var(var), term.de(state), pattern, explicit);
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

  record Univ(@NotNull SerLevel.Max u) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new FormTerm.Univ(u.de(state.levelCache));
    }
  }

  record Ref(@NotNull SimpVar var, @NotNull SerTerm type) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new RefTerm(state.var(var), type.de(state));
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
        Tuple.of(state.def(k), v.de(state)))));
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
    @NotNull ImmutableSeq<SerLevel.Max> sortArgs,
    @NotNull ImmutableSeq<SerArg> args
  ) implements Serializable {
    public @NotNull ImmutableSeq<Sort> de(@NotNull MutableMap<Integer, Sort.LvlVar> levelCache) {
      return sortArgs.map(max -> max.de(levelCache));
    }

    public @NotNull ImmutableSeq<Arg<Term>> de(@NotNull DeState state) {
      return args.map(arg -> arg.de(state));
    }
  }

  record StructCall(@NotNull SerDef.QName name, @NotNull CallData data) implements SerTerm {
    @Override public @NotNull CallTerm.Struct de(@NotNull DeState state) {
      return new CallTerm.Struct(state.def(name), data.de(state.levelCache), data.de(state));
    }
  }

  record FnCall(@NotNull SerDef.QName name, @NotNull CallData data) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new CallTerm.Fn(state.def(name), data.de(state.levelCache), data.de(state));
    }
  }

  record DataCall(@NotNull SerDef.QName name, @NotNull CallData data) implements SerTerm {
    @Override public @NotNull CallTerm.Data de(@NotNull DeState state) {
      return new CallTerm.Data(state.def(name), data.de(state.levelCache), data.de(state));
    }
  }

  record PrimCall(@NotNull SerDef.QName name, @NotNull PrimDef.ID id, @NotNull CallData data) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new CallTerm.Prim(state.def(name), id, data.de(state.levelCache), data.de(state));
    }
  }

  record ConCall(
    @NotNull SerDef.QName dataRef, @NotNull SerDef.QName selfRef,
    @NotNull CallData dataArgs, @NotNull ImmutableSeq<SerArg> args
  ) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new CallTerm.Con(
        state.def(dataRef), state.def(selfRef),
        dataArgs.de(state), dataArgs.de(state.levelCache),
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
    @NotNull ImmutableSeq<SerLevel.@NotNull Max> sortArgs,
    @NotNull ImmutableSeq<@NotNull SerArg> structArgs,
    @NotNull ImmutableSeq<@NotNull SerArg> fieldArgs
  ) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new CallTerm.Access(
        of.de(state), state.def(ref),
        sortArgs.map(max -> max.de(state.levelCache)),
        structArgs.map(arg -> arg.de(state)),
        fieldArgs.map(arg -> arg.de(state)));
    }
  }

  record FieldRef(@NotNull SerDef.QName name) implements SerTerm {
    @Override public @NotNull Term de(@NotNull DeState state) {
      return new RefTerm.Field(state.def(name));
    }
  }
}
