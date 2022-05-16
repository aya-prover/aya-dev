// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import kala.tuple.Unit;
import org.aya.core.Matching;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.*;
import org.aya.generic.Arg;
import org.aya.generic.util.InternalException;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record Serializer(@NotNull Serializer.State state) {

  private @NotNull SerPat serialize(@NotNull Pat pat) {
    return switch (pat) {
      case Pat.Absurd absurd -> new SerPat.Absurd(absurd.explicit());
      case Pat.Ctor ctor -> new SerPat.Ctor(
        ctor.explicit(),
        state.def(ctor.ref()),
        serializePats(ctor.params()),
        visitDataCall(ctor.type()));
      case Pat.Tuple tuple -> new SerPat.Tuple(tuple.explicit(), serializePats(tuple.pats()));
      case Pat.Bind bind -> new SerPat.Bind(bind.explicit(), state.local(bind.bind()), serialize(bind.type()));
      case Pat.End end -> new SerPat.End(end.isRight(), end.explicit());
      case Pat.Meta meta -> throw new InternalException(meta + " is illegal here");
      // TODO: serialize lit patterns
      case Pat.ShapedInt lit -> throw new UnsupportedOperationException("TODO");
    };
  }

  private @NotNull SerPat.Matchy serialize(@NotNull Matching matchy) {
    return new SerPat.Matchy(serializePats(matchy.patterns()), serialize(matchy.body()));
  }

  private SerTerm.SerArg serialize(@NotNull Arg<@NotNull Term> termArg) {
    return new SerTerm.SerArg(serialize(termArg.term()), termArg.explicit());
  }

  public record State(
    @NotNull MutableMap<LocalVar, Integer> localCache,
    @NotNull MutableMap<DefVar<?, ?>, Integer> defCache
  ) {
    public State() {
      this(MutableMap.create(), MutableMap.create());
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

  private @NotNull SerTerm serialize(@NotNull Term term) {
    return switch (term) {
      case CallTerm.Access access -> new SerTerm.Access(
        serialize(access.of()), state.def(access.ref()),
        serializeArgs(access.structArgs()),
        serializeArgs(access.fieldArgs())
      );
      case ElimTerm.Proj proj -> new SerTerm.Proj(serialize(proj.of()), proj.ix());
      case IntroTerm.New newTerm ->
        new SerTerm.New(visitStructCall(newTerm.struct()), ImmutableMap.from(newTerm.params().view().map((k, v) ->
          Tuple.of(state.def(k), serialize(v)))));
      case IntroTerm.Tuple tuple -> new SerTerm.Tup(tuple.items().map(this::serialize));
      case CallTerm.Prim prim ->
        new SerTerm.PrimCall(state.def(prim.ref()), prim.id(), serializeCall(prim.ulift(), prim.args()));
      case CallTerm.Struct structCall -> visitStructCall(structCall);
      case CallTerm.Con conCall -> new SerTerm.ConCall(
        state.def(conCall.head().dataRef()), state.def(conCall.head().ref()),
        serializeCall(conCall.head().ulift(), conCall.head().dataArgs()),
        serializeArgs(conCall.args())
      );
      case CallTerm.Data dataCall -> visitDataCall(dataCall);
      case CallTerm.Fn fnCall ->
        new SerTerm.FnCall(state.def(fnCall.ref()), serializeCall(fnCall.ulift(), fnCall.args()));
      case ElimTerm.App app -> new SerTerm.App(serialize(app.of()), serialize(app.arg()));
      case FormTerm.Sigma sigma -> new SerTerm.Sigma(serializeParams(sigma.params()));
      case FormTerm.Univ univ -> new SerTerm.Univ(univ.lift());
      case FormTerm.Pi pi -> new SerTerm.Pi(serialize(pi.param()), serialize(pi.body()));
      case FormTerm.Interval interval -> new SerTerm.Interval();
      case IntroTerm.Lambda lambda -> new SerTerm.Lam(serialize(lambda.param()), serialize(lambda.body()));
      case RefTerm.MetaPat metaPat -> throw new AssertionError("Shall not have metaPats serialized.");
      case RefTerm.Field fieldRef -> new SerTerm.FieldRef(state.def(fieldRef.ref()), fieldRef.lift());
      case RefTerm ref -> new SerTerm.Ref(state.local(ref.var()), ref.lift());
      case CallTerm.Hole hole -> throw new AssertionError("Shall not have holes serialized.");
      case PrimTerm.End end -> new SerTerm.End(end.isRight());
      case ErrorTerm error -> throw new AssertionError("Shall not have errors serialized.");
      case LitTerm.ShapedInt shapedInt -> throw new UnsupportedOperationException("TODO");
    };
  }

  private SerTerm.DataCall visitDataCall(@NotNull CallTerm.Data dataCall) {
    return new SerTerm.DataCall(
      state.def(dataCall.ref()),
      serializeCall(dataCall.ulift(), dataCall.args())
    );
  }

  private SerTerm.StructCall visitStructCall(@NotNull CallTerm.Struct structCall) {
    return new SerTerm.StructCall(
      state.def(structCall.ref()),
      serializeCall(structCall.ulift(), structCall.args())
    );
  }

  private @NotNull ImmutableSeq<SerTerm.SerArg> serializeArgs(@NotNull ImmutableSeq<Arg<Term>> args) {
    return args.map(this::serialize);
  }

  private @NotNull ImmutableSeq<SerPat> serializePats(@NotNull ImmutableSeq<Pat> pats) {
    return pats.map(this::serialize);
  }

  private @NotNull SerTerm.CallData serializeCall(
    int ulift,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> args) {
    return new SerTerm.CallData(ulift, serializeArgs(args));
  }

  public @NotNull SerDef serialize(@NotNull Def def) {
    return switch (def) {
      case PrimDef prim -> {
        assert prim.ref.module != null;
        yield new SerDef.Prim(prim.ref.module, prim.id);
      }
      case FieldDef field -> visitField(field);
      case StructDef struct -> new SerDef.Struct(
        state.def(struct.ref()),
        serializeParams(struct.telescope),
        struct.resultLevel,
        struct.fields.map(this::visitField)
      );
      case CtorDef ctor -> visitCtor(ctor);
      case DataDef data -> new SerDef.Data(
        state.def(data.ref()),
        serializeParams(data.telescope),
        data.resultLevel,
        data.body.map(this::visitCtor)
      );
      case FnDef fn -> new SerDef.Fn(state.def(fn.ref), serializeParams(fn.telescope),
        fn.body.map(this::serialize, matchings -> matchings.map(this::serialize)),
        fn.modifiers, serialize(fn.result));
    };
  }

  private SerDef.Ctor visitCtor(@NotNull CtorDef def) {
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

  private SerDef.Field visitField(@NotNull FieldDef def) {
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
}
