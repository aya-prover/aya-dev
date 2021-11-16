// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableArray;
import kala.collection.mutable.MutableMap;
import kala.tuple.Unit;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.core.Matching;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author ice1000
 */
public record Serializer(@NotNull Serializer.State state) implements
  Term.Visitor<Unit, SerTerm>,
  Def.Visitor<Unit, SerDef>,
  Pat.Visitor<Unit, SerPat> {
  private @NotNull SerTerm serialize(@NotNull Term term) {
    return term.accept(this, Unit.unit());
  }

  private @NotNull SerPat serialize(@NotNull Pat pat) {
    return pat.accept(this, Unit.unit());
  }

  private @NotNull SerPat.Matchy serialize(@NotNull Matching matchy) {
    return new SerPat.Matchy(serializePats(matchy.patterns()), serialize(matchy.body()));
  }

  private SerTerm.SerArg serialize(@NotNull Arg<@NotNull Term> termArg) {
    return new SerTerm.SerArg(serialize(termArg.term()), termArg.explicit());
  }

  public record State(
    @NotNull MutableMap<Sort.LvlVar, Integer> levelCache,
    @NotNull MutableMap<LocalVar, Integer> localCache,
    @NotNull MutableMap<DefVar<?, ?>, Integer> defCache
  ) {
    public State() {
      this(MutableMap.create(), MutableMap.create(), MutableMap.create());
    }

    public @NotNull SerTerm.SimpVar local(@NotNull LocalVar var) {
      return new SerTerm.SimpVar(localCache.getOrPut(var, localCache::size), var.name());
    }

    public @NotNull SerTerm.SimpVar localMaybe(@Nullable LocalVar var) {
      if (var == null) return new SerTerm.SimpVar(-1, "");
      else return local(var);
    }

    public @NotNull SerDef.QName def(@NotNull DefVar<?, ?> var) {
      assert var.module != null;
      return new SerDef.QName(var.module.toImmutableArray(), var.name(), defCache.getOrPut(var, defCache::size));
    }
  }

  @Contract("_ -> new") private SerTerm.SerParam serialize(Term.@NotNull Param param) {
    return new SerTerm.SerParam(param.explicit(), param.pattern(), state.local(param.ref()), serialize(param.type()));
  }

  private @NotNull ImmutableArray<SerTerm.SerParam> serializeParams(ImmutableArray<Term.@NotNull Param> params) {
    return params.map(this::serialize);
  }

  @Override public SerTerm visitError(@NotNull ErrorTerm term, Unit unit) {
    throw new AssertionError("Shall not have error term serialized.");
  }

  @Override public SerTerm visitHole(CallTerm.@NotNull Hole term, Unit unit) {
    throw new AssertionError("Shall not have holes serialized.");
  }

  @Override
  public SerTerm visitFieldRef(@NotNull RefTerm.Field term, Unit unit) {
    return new SerTerm.FieldRef(state.def(term.ref()));
  }

  @Override public SerTerm visitRef(@NotNull RefTerm term, Unit unit) {
    return new SerTerm.Ref(state.local(term.var()), serialize(term.type()));
  }

  @Override public SerTerm visitLam(IntroTerm.@NotNull Lambda term, Unit unit) {
    return new SerTerm.Lam(serialize(term.param()), serialize(term.body()));
  }

  @Override public SerTerm visitPi(FormTerm.@NotNull Pi term, Unit unit) {
    return new SerTerm.Pi(serialize(term.param()), serialize(term.body()));
  }

  @Override public SerTerm visitSigma(FormTerm.@NotNull Sigma term, Unit unit) {
    return new SerTerm.Sigma(serializeParams(term.params()));
  }

  @Override public SerTerm visitUniv(FormTerm.@NotNull Univ term, Unit unit) {
    return new SerTerm.Univ(serialize(term.sort()));
  }

  private @NotNull ImmutableArray<SerTerm.SerArg> serializeArgs(@NotNull ImmutableArray<Arg<Term>> args) {
    return args.map(this::serialize);
  }

  private SerLevel.@NotNull Max serialize(@NotNull Sort level) {
    return SerLevel.ser(level, state.levelCache());
  }

  private @NotNull ImmutableArray<SerLevel.Max> serializeLevels(@NotNull ImmutableArray<Sort> sortArgs) {
    return sortArgs.map(this::serialize);
  }

  private @NotNull ImmutableArray<SerPat> serializePats(@NotNull ImmutableArray<Pat> pats) {
    return pats.map(this::serialize);
  }

  @Override public SerTerm visitApp(ElimTerm.@NotNull App term, Unit unit) {
    return new SerTerm.App(serialize(term.of()), serialize(term.arg()));
  }

  private @NotNull SerTerm.CallData serializeCall(
    @NotNull ImmutableArray<@NotNull Sort> sortArgs,
    @NotNull ImmutableArray<Arg<@NotNull Term>> args) {
    return new SerTerm.CallData(serializeLevels(sortArgs), serializeArgs(args));
  }

  @Override public SerTerm visitFnCall(@NotNull CallTerm.Fn fnCall, Unit unit) {
    return new SerTerm.FnCall(state.def(fnCall.ref()), serializeCall(fnCall.sortArgs(), fnCall.args()));
  }

  @Override public SerTerm.DataCall visitDataCall(@NotNull CallTerm.Data dataCall, Unit unit) {
    return new SerTerm.DataCall(
      state.def(dataCall.ref()),
      serializeCall(dataCall.sortArgs(), dataCall.args())
    );
  }

  @Override public SerTerm visitConCall(@NotNull CallTerm.Con conCall, Unit unit) {
    return new SerTerm.ConCall(
      state.def(conCall.head().dataRef()), state.def(conCall.head().ref()),
      serializeCall(conCall.head().sortArgs(), conCall.head().dataArgs()),
      serializeArgs(conCall.args())
    );
  }

  @Override public SerTerm visitStructCall(@NotNull CallTerm.Struct structCall, Unit unit) {
    return new SerTerm.StructCall(
      state.def(structCall.ref()),
      serializeCall(structCall.sortArgs(), structCall.args())
    );
  }

  @Override public SerTerm visitPrimCall(CallTerm.@NotNull Prim prim, Unit unit) {
    return new SerTerm.PrimCall(state.def(prim.ref()), serializeCall(prim.sortArgs(), prim.args()));
  }

  @Override public SerTerm visitTup(IntroTerm.@NotNull Tuple term, Unit unit) {
    return new SerTerm.Tup(term.items().map(this::serialize));
  }

  @Override public SerTerm visitNew(IntroTerm.@NotNull New newTerm, Unit unit) {
    return new SerTerm.New(new SerTerm.StructCall(
      state.def(newTerm.struct().ref()),
      serializeCall(newTerm.struct().sortArgs(), newTerm.struct().args())
    ));
  }

  @Override public SerTerm visitProj(ElimTerm.@NotNull Proj term, Unit unit) {
    return new SerTerm.Proj(serialize(term.of()), term.ix());
  }

  @Override public SerTerm visitAccess(CallTerm.@NotNull Access term, Unit unit) {
    return new SerTerm.Access(
      serialize(term.of()), state.def(term.ref()),
      serializeLevels(term.sortArgs()),
      serializeArgs(term.structArgs()),
      serializeArgs(term.fieldArgs())
    );
  }

  @Override public SerPat visitBind(Pat.@NotNull Bind bind, Unit unit) {
    return new SerPat.Bind(bind.explicit(), state.local(bind.as()), serialize(bind.type()));
  }

  @Override public SerPat visitTuple(Pat.@NotNull Tuple tuple, Unit unit) {
    return new SerPat.Tuple(tuple.explicit(),
      serializePats(tuple.pats()), state.localMaybe(tuple.as()), serialize(tuple.type()));
  }

  @Override public SerPat visitCtor(Pat.@NotNull Ctor ctor, Unit unit) {
    return new SerPat.Ctor(
      ctor.explicit(),
      state.def(ctor.ref()),
      serializePats(ctor.params()),
      state.localMaybe(ctor.as()),
      visitDataCall(ctor.type(), unit));
  }

  @Override public SerPat visitAbsurd(Pat.@NotNull Absurd absurd, Unit unit) {
    return new SerPat.Absurd(absurd.explicit(), serialize(absurd.type()));
  }

  @Override public SerPat visitPrim(Pat.@NotNull Prim prim, Unit unit) {
    return new SerPat.Prim(prim.explicit(), state.def(prim.ref()), serialize(prim.type()));
  }

  @Override public SerDef visitFn(@NotNull FnDef def, Unit unit) {
    return new SerDef.Fn(state.def(def.ref), serializeParams(def.telescope),
      def.levels.map(lvl -> SerLevel.ser(lvl, state.levelCache)),
      def.body.map(this::serialize, matchings -> matchings.map(this::serialize)),
      def.modifiers, serialize(def.result));
  }

  @Override public SerDef visitData(@NotNull DataDef def, Unit unit) {
    return new SerDef.Data(
      state.def(def.ref),
      serializeParams(def.telescope),
      def.levels.map(lvl -> SerLevel.ser(lvl, state.levelCache)),
      serialize(def.result),
      def.body.map(ctor -> visitCtor(ctor, Unit.unit()))
    );
  }

  @Override public SerDef.Ctor visitCtor(@NotNull CtorDef def, Unit unit) {
    return new SerDef.Ctor(
      state.def(def.dataRef),
      state.def(def.ref),
      serializePats(def.pats),
      serializeParams(def.ownerTele),
      serializeParams(def.selfTele),
      def.clauses.map(this::serialize),
      serialize(def.result),
      def.coerce
    );
  }

  @Override public SerDef visitStruct(@NotNull StructDef def, Unit unit) {
    return new SerDef.Struct(
      state.def(def.ref()),
      serializeParams(def.telescope),
      def.levels.map(lvl -> SerLevel.ser(lvl, state.levelCache)),
      serialize(def.result),
      def.fields.map(field -> visitField(field, Unit.unit()))
    );
  }

  @Override public SerDef.Field visitField(@NotNull FieldDef def, Unit unit) {
    return new SerDef.Field(
      state.def(def.structRef),
      state.def(def.ref),
      serializeParams(def.ownerTele),
      serializeParams(def.selfTele),
      serialize(def.result),
      def.clauses.map(this::serialize),
      def.body.map(this::serialize),
      def.coerce
    );
  }

  @Override public SerDef visitPrim(@NotNull PrimDef def, Unit unit) {
    return new SerDef.Prim(
      serializeParams(def.telescope),
      def.levels.map(lvl -> SerLevel.ser(lvl, state.levelCache)),
      serialize(def.result),
      Objects.requireNonNull(PrimDef.ID.find(def.ref.name()))
    );
  }
}
