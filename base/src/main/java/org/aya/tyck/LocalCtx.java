// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.DynamicSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple2;
import org.aya.api.ref.LocalVar;
import org.aya.core.Meta;
import org.aya.core.term.CallTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.Term;
import org.aya.generic.Constants;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * @author re-xyr, ice1000
 */
@Debug.Renderer(hasChildren = "true", childrenArray = "extract().toArray()")
public record LocalCtx(@NotNull MutableMap<LocalVar, Term> localMap, @Nullable LocalCtx parent) {
  public LocalCtx() {
    this(MutableLinkedHashMap.of(), null);
  }

  public @NotNull Tuple2<CallTerm.Hole, Term> freshHole(@NotNull Term type, @NotNull SourcePos sourcePos) {
    return freshHole(type, Constants.ANONYMOUS_PREFIX, sourcePos);
  }

  public @NotNull Tuple2<CallTerm.Hole, Term> freshHole(@NotNull Term type, @NotNull String name, @NotNull SourcePos sourcePos) {
    var ctxTele = extract();
    var meta = Meta.from(ctxTele, name, type, sourcePos);
    var hole = new CallTerm.Hole(meta, ctxTele.map(Term.Param::toArg), meta.telescope.map(Term.Param::toArg));
    return Tuple2.of(hole, IntroTerm.Lambda.make(meta.telescope, hole));
  }

  public <T> T with(@NotNull Term.Param param, @NotNull Supplier<T> action) {
    return with(param.ref(), param.type(), action);
  }

  public <T> T with(@NotNull LocalVar var, @NotNull Term type, @NotNull Supplier<T> action) {
    localMap.put(var, type);
    try {
      return action.get();
    } finally {
      localMap.remove(var);
    }
  }

  public @NotNull ImmutableSeq<Term.Param> extract() {
    var ctx = DynamicSeq.<Term.Param>create();
    var map = this;
    while (map != null) {
      map.localMap.mapTo(ctx, (k, v) -> new Term.Param(k, v, false));
      map = map.parent;
    }
    return ctx.toImmutableSeq();
  }

  @Contract(pure = true) public @NotNull Term get(LocalVar var) {
    var result = localMap.getOrElse(var, () -> parentGet(var));
    assert result != null : var.name();
    return result;
  }

  @Contract(pure = true) private @Nullable Term parentGet(LocalVar var) {
    return parent != null ? parent.get(var) : null;
  }

  public void put(@NotNull Term.Param param) {
    put(param.ref(), param.type());
  }

  public void put(@NotNull LocalVar var, @NotNull Term term) {
    localMap.set(var, term);
  }

  public boolean isEmpty() {
    return localMap.isEmpty() && (parent == null || parent.isEmpty());
  }

  @Contract(" -> new") public @NotNull LocalCtx derive() {
    return new LocalCtx(MutableLinkedHashMap.of(), this);
  }
}
