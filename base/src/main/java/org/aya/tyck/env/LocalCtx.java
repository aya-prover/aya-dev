// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.env;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableList;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.core.UntypedParam;
import org.aya.core.meta.Meta;
import org.aya.core.term.IntervalTerm;
import org.aya.core.term.LamTerm;
import org.aya.core.term.MetaTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.VarConsumer;
import org.aya.generic.Constants;
import org.aya.generic.util.InternalException;
import org.aya.ref.AnyVar;
import org.aya.ref.LocalVar;
import org.aya.tyck.tycker.TyckState;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@Debug.Renderer(hasChildren = "true", childrenArray = "extract().toArray()")
public sealed interface LocalCtx permits MapLocalCtx, SeqLocalCtx {
  @NotNull default Tuple2<MetaTerm, Term> freshHole(@NotNull Term type, @NotNull SourcePos sourcePos) {
    return freshHole(type, Constants.ANONYMOUS_PREFIX, sourcePos);
  }
  default @NotNull Tuple2<MetaTerm, Term>
  freshHole(@NotNull Term type, @NotNull String name, @NotNull SourcePos sourcePos) {
    var ctxTele = extract();
    var meta = Meta.from(ctxTele, name, type, sourcePos);
    var view = meta.telescope.map(LamTerm::param);
    var hole = new MetaTerm(meta, ctxTele.map(Term.Param::toArg), view.map(UntypedParam::toArg));
    return Tuple.of(hole, LamTerm.make(view, hole));
  }
  default @NotNull Tuple2<MetaTerm, Term>
  freshTyHole(@NotNull String name, @NotNull SourcePos sourcePos) {
    var ctxTele = extract();
    var meta = Meta.from(ctxTele, name, sourcePos);
    var view = meta.telescope.map(LamTerm::param);
    var hole = new MetaTerm(meta, ctxTele.map(Term.Param::toArg), view.map(UntypedParam::toArg));
    return Tuple.of(hole, LamTerm.make(view, hole));
  }
  default <T> T with(@NotNull Term.Param param, @NotNull Supplier<T> action) {
    return with(param.ref(), param.type(), action);
  }
  default <T> T with(@NotNull Seq<Term.Param> params, @NotNull Supplier<T> action) {
    if (params.isEmpty()) return action.get();
    params.forEach(x -> put(x.ref(), x.type()));
    try {
      return action.get();
    } finally {
      remove(params.view().map(Term.Param::ref));
    }
  }
  default <T> T withIntervals(@NotNull SeqView<LocalVar> params, @NotNull Supplier<T> action) {
    if (params.isEmpty()) return action.get();
    params.forEach(x -> put(x, IntervalTerm.INSTANCE));
    try {
      return action.get();
    } finally {
      remove(params);
    }
  }
  void remove(@NotNull SeqView<LocalVar> vars);
  default void forward(@NotNull LocalCtx dest, @NotNull Term term, @NotNull TyckState state) {
    new VarConsumer.Scoped() {
      @Override public void var(@NotNull AnyVar var) {
        if (bound.contains(var)) return;
        switch (var) {
          // This is intentional, we don't want to check for duplication in forwarding
          case LocalVar localVar -> dest.putUnchecked(localVar, get(localVar));
          case Meta meta -> {
            var sol = state.metas().getOrNull(meta);
            if (sol != null) forward(dest, sol, state);
          }
          case default -> {}
        }
      }
    }.accept(term);
  }
  default <T> T with(@NotNull LocalVar var, @NotNull Term type, @NotNull Supplier<T> action) {
    put(var, type);
    try {
      return action.get();
    } finally {
      remove(SeqView.of(var));
    }
  }
  default <T> T with(@NotNull Supplier<T> action, @NotNull Term.Param... param) {
    return with(action, Seq.of(param).view());
  }
  default <T> T with(@NotNull Supplier<T> action, @NotNull SeqView<Term.Param> param) {
    for (var p : param) put(p);
    try {
      return action.get();
    } finally {
      remove(param.map(Term.Param::ref));
    }
  }
  default @NotNull ImmutableSeq<Term.Param> extract() {
    var ctx = MutableList.<Term.Param>create();
    var map = this;
    while (map != null) {
      map.extractToLocal(ctx);
      map = map.parent();
    }
    return ctx.toImmutableSeq();
  }
  @Contract(mutates = "param1") void extractToLocal(@NotNull MutableList<Term.Param> dest);
  @Contract(pure = true) default @NotNull Term get(@NotNull LocalVar var) {
    var res = getUnchecked(var);
    if (res != null) return res;
    throw new InternalException(var.name());
  }

  @Contract(pure = true) default @Nullable Term getUnchecked(@NotNull LocalVar var) {
    var ctx = this;
    while (ctx != null) {
      var res = ctx.getLocal(var);
      if (res != null) return res;
      ctx = ctx.parent();
    }
    return null;
  }

  @Contract(pure = true) default boolean contains(@NotNull LocalVar var) {
    return getUnchecked(var) != null;
  }

  @Contract(pure = true) @Nullable Term getLocal(@NotNull LocalVar var);
  default void put(@NotNull Term.Param param) {
    put(param.ref(), param.type());
  }

  /**
   * Put a <b>new</b> var into the current local context
   *
   * @param var  a <b>new</b> var
   * @param term the type of that var
   * @implNote panic is expected when the var is duplicated
   */
  default void put(@NotNull LocalVar var, @NotNull Term term) {
    assert !contains(var);
    if (var != LocalVar.IGNORED) putUnchecked(var, term);
  }

  /**
   * DON'T USE THIS, USE {@link LocalCtx#put(LocalVar, Term)} instead.
   */
  void putUnchecked(@NotNull LocalVar var, @NotNull Term tern);
  default boolean isEmpty() {
    if (isMeEmpty()) {
      var parent = parent();
      return parent == null || parent.isEmpty();
    }
    return false;
  }
  boolean isMeEmpty();
  /**
   * Whether to choose map or seq is completely random in Aya.
   *
   * @see #deriveSeq()
   */
  @Contract(" -> new") default @NotNull MapLocalCtx deriveMap() {
    return new MapLocalCtx(MutableLinkedHashMap.of(), this);
  }
  /**
   * @see #deriveMap()
   */
  @Contract(" -> new") default @NotNull SeqLocalCtx deriveSeq() {
    return new SeqLocalCtx(MutableList.create(), this);
  }
  @Nullable LocalCtx parent();
  @Contract(mutates = "this") void modifyMyTerms(@NotNull UnaryOperator<Term> u);
}
