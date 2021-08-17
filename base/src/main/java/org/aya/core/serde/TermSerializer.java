// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.tuple.Unit;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.Arg;
import org.aya.core.pat.Pat;
import org.aya.core.sort.Sort;
import org.aya.core.term.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public final class TermSerializer implements
  Term.Visitor<Unit, SerTerm>,
  Pat.Visitor<Unit, SerPat> {
  public final @NotNull SerState state;

  public TermSerializer(@NotNull SerState state) {
    this.state = state;
  }

  public @NotNull SerTerm serialize(@NotNull Term term) {
    return term.accept(this, Unit.unit());
  }

  public @NotNull SerPat serialize(@NotNull Pat pat) {
    return pat.accept(this, Unit.unit());
  }

  private SerTerm.SerArg serializeArg(@NotNull Arg<@NotNull Term> termArg) {
    return new SerTerm.SerArg(
      serialize(termArg.term()),
      termArg.explicit()
    );
  }

  public static record SerState(
    @NotNull MutableMap<Sort.LvlVar, Integer> levelCache,
    @NotNull MutableMap<LocalVar, Integer> localCache,
    @NotNull MutableMap<DefVar<?, ?>, Integer> defCache
  ) {
    public @NotNull SerTerm.SimpVar local(@NotNull LocalVar var) {
      return new SerTerm.SimpVar(localCache.getOrPut(var, localCache::size), var.name());
    }

    public @NotNull SerTerm.SimpVar localMaybe(@Nullable LocalVar var) {
      if (var == null) return new SerTerm.SimpVar(-1, "");
      else return local(var);
    }

    public @NotNull SerDef.QName def(@NotNull DefVar<?, ?> var) {
      // todo: mod
      return new SerDef.QName(
        ImmutableSeq.empty(), // fixme
        var.name(),
        defCache.getOrPut(var, localCache::size)
      );
    }
  }

  private SerTerm.SerParam serializeParam(Term.Param param) {
    return new SerTerm.SerParam(
      param.explicit(),
      state.local(param.ref()),
      serialize(param.type())
    );
  }

  @Override public SerTerm visitError(@NotNull ErrorTerm term, Unit unit) {
    throw new AssertionError("Shall not have error term serialized.");
  }

  @Override public SerTerm visitHole(CallTerm.@NotNull Hole term, Unit unit) {
    throw new AssertionError("Shall not have holes serialized.");
  }

  @Override public SerTerm visitRef(@NotNull RefTerm term, Unit unit) {
    return new SerTerm.Ref(state.local(term.var()), serialize(term.type()));
  }

  @Override public SerTerm visitLam(IntroTerm.@NotNull Lambda term, Unit unit) {
    return new SerTerm.Lam(serializeParam(term.param()), serialize(term.body()));
  }

  @Override public SerTerm visitPi(FormTerm.@NotNull Pi term, Unit unit) {
    return new SerTerm.Pi(serializeParam(term.param()), serialize(term.body()));
  }

  @Override public SerTerm visitSigma(FormTerm.@NotNull Sigma term, Unit unit) {
    return new SerTerm.Sigma(term.params().map(this::serializeParam));
  }

  @Override public SerTerm visitUniv(FormTerm.@NotNull Univ term, Unit unit) {
    return new SerTerm.Univ(
      SerLevel.ser(term.sort().uLevel(), state.levelCache()),
      SerLevel.ser(term.sort().hLevel(), state.levelCache())
    );
  }

  @Override public SerTerm visitApp(ElimTerm.@NotNull App term, Unit unit) {
    return new SerTerm.App(serialize(term.of()), serializeArg(term.arg()));
  }

  private @NotNull SerTerm.CallData serializeCallData(
    @NotNull ImmutableSeq<Sort.@NotNull CoreLevel> sortArgs,
    @NotNull ImmutableSeq<Arg<@NotNull Term>> args) {
    return new SerTerm.CallData(
      sortArgs.map(coreLevel -> SerLevel.ser(coreLevel, state.levelCache())),
      args.map(this::serializeArg));
  }

  @Override public SerTerm visitFnCall(@NotNull CallTerm.Fn fnCall, Unit unit) {
    return new SerTerm.FnCall(
      state.def(fnCall.ref()),
      serializeCallData(fnCall.sortArgs(), fnCall.args())
    );
  }

  @Override public SerTerm visitDataCall(@NotNull CallTerm.Data dataCall, Unit unit) {
    return new SerTerm.DataCall(
      state.def(dataCall.ref()),
      serializeCallData(dataCall.sortArgs(), dataCall.args())
    );
  }

  @Override public SerTerm visitConCall(@NotNull CallTerm.Con conCall, Unit unit) {
    return new SerTerm.ConCall(
      state.def(conCall.head().dataRef()),
      state.def(conCall.head().ref()),
      serializeCallData(conCall.head().sortArgs(), conCall.head().dataArgs()),
      conCall.args().map(termArg -> new SerTerm.SerArg(
        serialize(termArg.term()),
        termArg.explicit()
      ))
    );
  }

  @Override public SerTerm visitStructCall(@NotNull CallTerm.Struct structCall, Unit unit) {
    return new SerTerm.StructCall(
      state.def(structCall.ref()),
      serializeCallData(structCall.sortArgs(), structCall.args())
    );
  }

  @Override public SerTerm visitPrimCall(CallTerm.@NotNull Prim prim, Unit unit) {
    return new SerTerm.PrimCall(
      state.def(prim.ref()),
      serializeCallData(prim.sortArgs(), prim.args())
    );
  }

  @Override public SerTerm visitTup(IntroTerm.@NotNull Tuple term, Unit unit) {
    return new SerTerm.Tup(
      term.items().map(this::serialize)
    );
  }

  @Override public SerTerm visitNew(IntroTerm.@NotNull New newTerm, Unit unit) {
    return new SerTerm.New(new SerTerm.StructCall(
      state.def(newTerm.struct().ref()),
      serializeCallData(newTerm.struct().sortArgs(), newTerm.struct().args())
    ));
  }

  @Override public SerTerm visitProj(ElimTerm.@NotNull Proj term, Unit unit) {
    return new SerTerm.Proj(
      serialize(term.of()),
      term.ix()
    );
  }

  @Override public SerTerm visitAccess(CallTerm.@NotNull Access term, Unit unit) {
    return new SerTerm.Access(
      serialize(term.of()),
      state.def(term.ref()),
      term.sortArgs().map(coreLevel -> SerLevel.ser(coreLevel, state.levelCache())),
      term.structArgs().map(this::serializeArg),
      term.fieldArgs().map(this::serializeArg)
    );
  }

  @Override public SerPat visitBind(Pat.@NotNull Bind bind, Unit unit) {
    return new SerPat.Bind(bind.explicit(), state.local(bind.as()), serialize(bind.type()));
  }

  @Override public SerPat visitTuple(Pat.@NotNull Tuple tuple, Unit unit) {
    return new SerPat.Tuple(tuple.explicit(),
      tuple.pats().map(this::serialize), state.localMaybe(tuple.as()), serialize(tuple.type()));
  }

  @Override public SerPat visitCtor(Pat.@NotNull Ctor ctor, Unit unit) {
    return new SerPat.Ctor(
      ctor.explicit(),
      state.def(ctor.ref()),
      ctor.params().map(this::serialize),
      state.localMaybe(ctor.as()),
      new SerTerm.DataCall(
        state.def(ctor.type().ref()),
        serializeCallData(ctor.type().sortArgs(), ctor.type().args())
      )
    );
  }

  @Override public SerPat visitAbsurd(Pat.@NotNull Absurd absurd, Unit unit) {
    return new SerPat.Absurd(absurd.explicit(), serialize(absurd.type()));
  }

  @Override public SerPat visitPrim(Pat.@NotNull Prim prim, Unit unit) {
    return new SerPat.Prim(
      prim.explicit(),
      state.def(prim.ref()),
      serialize(prim.type())
    );
  }
}
