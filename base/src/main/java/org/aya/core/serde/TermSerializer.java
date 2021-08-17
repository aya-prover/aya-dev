// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.serde;

import kala.collection.mutable.MutableMap;
import kala.tuple.Unit;
import org.aya.api.ref.DefVar;
import org.aya.api.ref.LocalVar;
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

  @Override public SerPat visitBind(Pat.@NotNull Bind bind, Unit unit) {
    return new SerPat.Bind(bind.explicit(), state.local(bind.as()), serialize(bind.type()));
  }

  @Override public SerPat visitTuple(Pat.@NotNull Tuple tuple, Unit unit) {
    return new SerPat.Tuple(tuple.explicit(),
      tuple.pats().map(this::serialize), state.localMaybe(tuple.as()), serialize(tuple.type()));
  }

  @Override public SerPat visitCtor(Pat.@NotNull Ctor ctor, Unit unit) {
    throw new UnsupportedOperationException("TODO");
  }

  @Override public SerPat visitAbsurd(Pat.@NotNull Absurd absurd, Unit unit) {
    return new SerPat.Absurd(absurd.explicit(), serialize(absurd.type()));
  }

  @Override public SerPat visitPrim(Pat.@NotNull Prim prim, Unit unit) {
    throw new UnsupportedOperationException("TODO");
  }
}
