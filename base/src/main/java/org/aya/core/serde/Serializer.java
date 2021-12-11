// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import kala.tuple.Unit;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.core.Matching;
import org.aya.core.TypedMatching;
import org.aya.core.def.*;
import org.aya.core.pat.Lhs;
import org.aya.core.pat.Pat;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record Serializer(@NotNull Serializer.State state) implements
  Term.Visitor<Unit, SerTerm>,
  Def.Visitor<Unit, SerDef> {
  private @NotNull SerTerm serialize(@NotNull Term term) {
    return term.accept(this, Unit.unit());
  }

  private @NotNull SerPat serialize(@NotNull Pat pat) {
    return switch (pat) {
      case Pat.Absurd absurd -> new SerPat.Absurd(absurd.explicit(), serialize(absurd.type()));
      case Pat.Ctor ctor -> new SerPat.Ctor(
        ctor.explicit(),
        state.def(ctor.ref()),
        serializePats(ctor.params()),
        visitDataCall(ctor.type(), Unit.unit()));
      case Pat.Prim prim -> new SerPat.Prim(prim.explicit(), state.def(prim.ref()), serialize(prim.type()));
      case Pat.Tuple tuple -> new SerPat.Tuple(tuple.explicit(),
        serializePats(tuple.pats()), serialize(tuple.type()));
      case Pat.Bind bind -> new SerPat.Bind(bind.explicit(), state.local(bind.bind()), serialize(bind.type()));
      case Pat.Meta meta -> throw new IllegalArgumentException(meta.toString());
    };
  }

  private @NotNull SerLhs serialize(@NotNull Lhs lhs) {
    return switch (lhs) {
      case Lhs.Ctor ctor -> new SerLhs.Ctor(
        ctor.explicit(),
        state.def(ctor.ref()),
        serializeLhss(ctor.params()),
        visitDataCall(ctor.type(), Unit.unit()));
      case Lhs.Prim prim -> new SerLhs.Prim(prim.explicit(), state.def(prim.ref()));
      case Lhs.Tuple tuple -> new SerLhs.Tuple(tuple.explicit(), serializeLhss(tuple.lhss()));
      case Lhs.Bind bind -> new SerLhs.Bind(bind.explicit(), state.local(bind.bind()), serialize(bind.type()));
    };
  }

  private @NotNull SerLhs.Matchy serialize(@NotNull Matching matchy) {
    return new SerLhs.Matchy(serializeLhss(matchy.lhss()), serialize(matchy.body()));
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

    public @NotNull SerDef.QName def(@NotNull DefVar<?, ?> var) {
      assert var.module != null;
      return new SerDef.QName(var.module, var.name());
    }
  }

  @Contract("_ -> new") private SerTerm.SerParam serialize(Term.@NotNull Param param) {
    return new SerTerm.SerParam(param.explicit(), param.pattern(), state.local(param.ref()), serialize(param.type()));
  }

  private @NotNull ImmutableSeq<SerTerm.SerParam> serializeParams(ImmutableSeq<Term.@NotNull Param> params) {
    return params.map(this::serialize);
  }

  @Override public SerTerm visitError(@NotNull ErrorTerm term, Unit unit) {
    throw new AssertionError("Shall not have error term serialized.");
  }

  @Override public SerTerm visitMetaPat(RefTerm.@NotNull MetaPat metaPat, Unit unit) {
    throw new AssertionError("Shall not have metaPats serialized.");
  }

  @Override public SerTerm visitHole(CallTerm.@NotNull Hole term, Unit unit) {
    throw new AssertionError("Shall not have holes serialized.");
  }

  @Override
  public SerTerm visitFieldRef(@NotNull RefTerm.Field term, Unit unit) {
    return new SerTerm.FieldRef(state.def(term.ref()));
  }

  @Override public SerTerm visitRef(@NotNull RefTerm term, Unit unit) {
    return new SerTerm.Ref(state.local(term.var()));
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

  private @NotNull ImmutableSeq<SerTerm.SerArg> serializeArgs(@NotNull ImmutableSeq<Arg<Term>> args) {
    return args.map(this::serialize);
  }

  private SerLevel.@NotNull Max serialize(@NotNull Sort level) {
    return SerLevel.ser(level, state.levelCache());
  }

  private @NotNull ImmutableSeq<SerLevel.Max> serializeLevels(@NotNull ImmutableSeq<Sort> sortArgs) {
    return sortArgs.map(this::serialize);
  }

  private @NotNull ImmutableSeq<SerPat> serializePats(@NotNull ImmutableSeq<Pat> pats) {
    return pats.map(this::serialize);
  }

  private @NotNull ImmutableSeq<SerLhs> serializeLhss(@NotNull ImmutableSeq<Lhs> lhss) {
    return lhss.map(this::serialize);
  }

  @Override public SerTerm visitApp(ElimTerm.@NotNull App term, Unit unit) {
    return new SerTerm.App(serialize(term.of()), serialize(term.arg()));
  }

  private @NotNull SerTerm.CallData serializeCall(
    @NotNull ImmutableSeq<@NotNull Sort> sortArgs,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> args) {
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

  @Override public SerTerm.StructCall visitStructCall(@NotNull CallTerm.Struct structCall, Unit unit) {
    return new SerTerm.StructCall(
      state.def(structCall.ref()),
      serializeCall(structCall.sortArgs(), structCall.args())
    );
  }

  @Override public SerTerm visitPrimCall(CallTerm.@NotNull Prim prim, Unit unit) {
    return new SerTerm.PrimCall(state.def(prim.ref()), prim.id(), serializeCall(prim.sortArgs(), prim.args()));
  }

  @Override public SerTerm visitTup(IntroTerm.@NotNull Tuple term, Unit unit) {
    return new SerTerm.Tup(term.items().map(this::serialize));
  }

  @Override public SerTerm visitNew(IntroTerm.@NotNull New newTerm, Unit unit) {
    return new SerTerm.New(visitStructCall(newTerm.struct(), unit), ImmutableMap.from(newTerm.params().view().map((k, v) ->
      Tuple.of(state.def(k), serialize(v)))));
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
      serialize(def.resultSort),
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
      serialize(def.resultSort),
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
    return new SerDef.Prim(def.ref.module, def.id);
  }
}
