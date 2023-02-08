// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.*;
import org.aya.generic.util.InternalException;
import org.aya.guest0x0.cubical.Partial;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record Serializer(@NotNull Serializer.State state) {
  public @NotNull SerDef serialize(@NotNull GenericDef def) {
    return switch (def) {
      case ClassDef classDef -> throw new UnsupportedOperationException("TODO");
      case FnDef fn -> new SerDef.Fn(
        state.def(fn.ref),
        serializeParams(fn.telescope),
        fn.body.map(this::serialize, matchings -> matchings.map(this::serialize)),
        fn.modifiers,
        serialize(fn.result)
      );
      case FieldDef field -> new SerDef.Field(
        state.def(field.structRef),
        state.def(field.ref),
        serializeParams(field.ownerTele),
        serializeParams(field.selfTele),
        serialize(field.result),
        field.body.map(this::serialize),
        field.coerce
      );
      case StructDef struct -> new SerDef.Struct(
        state.def(struct.ref()),
        serializeParams(struct.telescope),
        serialize(struct.result),
        struct.fields.map(field -> (SerDef.Field) serialize(field))
      );
      case DataDef data -> new SerDef.Data(
        state.def(data.ref),
        serializeParams(data.telescope),
        serialize(data.result),
        data.body.map(ctor -> (SerDef.Ctor) serialize(ctor))
      );
      case PrimDef prim -> {
        assert prim.ref.module != null;
        yield new SerDef.Prim(prim.ref.module, prim.id);
      }
      case CtorDef ctor -> new SerDef.Ctor(
        state.def(ctor.dataRef),
        state.def(ctor.ref),
        serializePats(ctor.pats),
        serializeParams(ctor.ownerTele),
        serializeParams(ctor.selfTele),
        ctor.clauses.fmap(this::serialize),
        serialize(ctor.result),
        ctor.coerce
      );
    };
  }

  private @NotNull SerTerm.Sort serialize(@NotNull SortTerm term) {
    return new SerTerm.Sort(term.kind(), term.lift());
  }

  private @NotNull SerTerm serialize(@NotNull Term term) {
    return switch (term) {
      case IntegerTerm lit -> new SerTerm.ShapedInt(lit.repr(),
        SerDef.SerShapeResult.serialize(state, lit.recognition()),
        (SerTerm.Data) serialize(lit.type()));
      case ListTerm lit -> new SerTerm.ShapedList(
        lit.repr().map(this::serialize),
        SerDef.SerShapeResult.serialize(state, lit.recognition()),
        (SerTerm.Data) serialize(lit.type()));
      case FormulaTerm end -> new SerTerm.Mula(end.asFormula().fmap(this::serialize));
      case StringTerm str -> new SerTerm.Str(str.string());
      case RefTerm ref -> new SerTerm.Ref(state.local(ref.var()));
      case RefTerm.Field ref -> new SerTerm.FieldRef(state.def(ref.ref()));
      case IntervalTerm interval -> new SerTerm.Interval();
      case PiTerm pi -> new SerTerm.Pi(serialize(pi.param()), serialize(pi.body()));
      case SigmaTerm sigma -> new SerTerm.Sigma(serializeParams(sigma.params()));
      case ConCall conCall -> new SerTerm.Con(
        state.def(conCall.head().dataRef()), state.def(conCall.head().ref()),
        serializeCall(conCall.head().ulift(), conCall.head().dataArgs()),
        serializeArgs(conCall.conArgs()));
      case ClassCall classCall -> serializeStructCall(classCall);
      case DataCall dataCall -> serializeDataCall(dataCall);
      case PrimCall prim -> new SerTerm.Prim(
        state.def(prim.ref()),
        prim.id(),
        serializeCall(prim.ulift(), prim.args()));
      case FieldTerm access -> new SerTerm.Access(
        serialize(access.of()), state.def(access.ref()),
        serializeArgs(access.structArgs()),
        serializeArgs(access.fieldArgs()));
      case FnCall fnCall -> new SerTerm.Fn(
        state.def(fnCall.ref()),
        serializeCall(fnCall.ulift(), fnCall.args()));
      case ProjTerm proj -> new SerTerm.Proj(serialize(proj.of()), proj.ix());
      case AppTerm app -> new SerTerm.App(serialize(app.of()), serialize(app.arg()));
      case MatchTerm(var disc, var clauses) ->
        new SerTerm.Match(disc.map(this::serialize), clauses.map(this::serialize));
      case TupTerm tuple -> new SerTerm.Tup(serializeArgs(tuple.items()));
      case LamTerm(var param, var body) -> new SerTerm.Lam(serialize(param.ref()), param.explicit(), serialize(body));
      case NewTerm newTerm -> new SerTerm.New(serializeStructCall(newTerm.struct()), ImmutableMap.from(
        newTerm.params().view().map((k, v) -> Tuple.of(state.def(k), serialize(v)))));

      case PartialTerm el -> new SerTerm.PartEl(partial(el.partial()), serialize(el.rhsType()));
      case PartialTyTerm ty -> new SerTerm.PartTy(serialize(ty.type()), ty.restr().fmap(this::serialize));
      case PathTerm path -> serialize(path);
      case PLamTerm path -> new SerTerm.PathLam(serializeIntervals(path.params()), serialize(path.body()));
      case PAppTerm app -> new SerTerm.PathApp(serialize(app.of()),
        serializeArgs(app.args()), serialize(app.cube()));
      case CoeTerm coe -> new SerTerm.Coe(serialize(coe.type()), coe.restr().fmap(this::serialize));
      case SortTerm sort -> serialize(sort);

      case MetaTerm hole -> throw new InternalException("Shall not have holes serialized.");
      case MetaPatTerm metaPat -> throw new InternalException("Shall not have metaPats serialized.");
      case ErrorTerm err -> throw new InternalException("Shall not have error term serialized.");
      case MetaLitTerm err -> throw new InternalException("Shall not have metaLiterals serialized.");
      case HCompTerm hComp -> throw new InternalException("TODO");
      case InTerm(var phi, var u) -> new SerTerm.InS(serialize(phi), serialize(u));
      case OutTerm(var phi, var par, var u) -> new SerTerm.OutS(serialize(phi), serialize(par), serialize(u));
    };
  }

  private @NotNull Partial<SerTerm> partial(Partial<Term> el) {
    return el.fmap(this::serialize);
  }

  private @NotNull SerPat serialize(@NotNull Pat pat, boolean explicit) {
    return switch (pat) {
      case Pat.Absurd absurd -> new SerPat.Absurd(explicit);
      case Pat.Ctor ctor -> new SerPat.Ctor(
        explicit,
        state.def(ctor.ref()),
        serializePats(ctor.params()),
        serializeDataCall(ctor.type()));
      case Pat.Tuple tuple -> new SerPat.Tuple(explicit, serializePats(tuple.pats()));
      case Pat.Bind bind -> new SerPat.Bind(explicit, state.local(bind.bind()), serialize(bind.type()));
      case Pat.Meta meta -> throw new InternalException(meta + " is illegal here");
      case Pat.ShapedInt lit -> new SerPat.ShapedInt(
        lit.repr(), explicit,
        SerDef.SerShapeResult.serialize(state, lit.recognition()),
        serializeDataCall(lit.type()));
    };
  }

  private @NotNull SerTerm.Path serialize(@NotNull PathTerm cube) {
    return new SerTerm.Path(
      serializeIntervals(cube.params()),
      serialize(cube.type()),
      partial(cube.partial()));
  }

  private @NotNull SerTerm.Data serializeDataCall(@NotNull DataCall dataCall) {
    return new SerTerm.Data(
      state.def(dataCall.ref()),
      serializeCall(dataCall.ulift(), dataCall.args()));
  }

  private @NotNull SerTerm.Struct serializeStructCall(@NotNull ClassCall classCall) {
    return new SerTerm.Struct(
      state.def(classCall.ref()),
      serializeCall(classCall.ulift(), classCall.args()));
  }

  private @NotNull SerPat.Clause serialize(@NotNull Term.Matching matchy) {
    return new SerPat.Clause(serializePats(matchy.patterns()), serialize(matchy.body()));
  }

  private SerTerm.SerArg serialize(@NotNull Arg<@NotNull Term> termArg) {
    return new SerTerm.SerArg(serialize(termArg.term()), termArg.explicit());
  }

  public record State(@NotNull MutableMap<LocalVar, Integer> localCache) {
    public State() {
      this(MutableMap.create());
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
    return new SerTerm.SerParam(param.explicit(), serialize(param.ref()), serialize(param.type()));
  }

  private @NotNull SerTerm.SimpVar serialize(@NotNull LocalVar localVar) {
    return state.local(localVar);
  }

  private @NotNull ImmutableSeq<SerTerm.SerParam> serializeParams(ImmutableSeq<Term.@NotNull Param> params) {
    return params.map(this::serialize);
  }

  private @NotNull ImmutableSeq<SerTerm.SimpVar> serializeIntervals(ImmutableSeq<LocalVar> params) {
    return params.map(this::serialize);
  }

  private @NotNull ImmutableSeq<SerTerm.SerArg> serializeArgs(@NotNull ImmutableSeq<Arg<Term>> args) {
    return args.map(this::serialize);
  }

  private @NotNull ImmutableSeq<SerPat> serializePats(@NotNull ImmutableSeq<Arg<Pat>> pats) {
    return pats.map(x -> serialize(x.term(), x.explicit()));
  }

  private @NotNull SerTerm.CallData serializeCall(
    int ulift,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> args) {
    return new SerTerm.CallData(ulift, serializeArgs(args));
  }
}
