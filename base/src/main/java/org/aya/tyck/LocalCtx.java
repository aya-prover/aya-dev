// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck;

import org.aya.api.error.SourcePos;
import org.aya.api.ref.HoleVar;
import org.aya.api.ref.LocalVar;
import org.aya.core.Meta;
import org.aya.core.term.CallTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.Term;
import org.aya.util.Constants;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import kala.tuple.Tuple2;
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
    this(MutableHashMap.of(), null);
  }

  public @NotNull Tuple2<Meta, Term> freshHole(@NotNull Term type, @NotNull SourcePos sourcePos) {
    return freshHole(type, Constants.ANONYMOUS_PREFIX, sourcePos);
  }

  public @NotNull Tuple2<Meta, Term> freshHole(@NotNull Term type, @NotNull String name, @NotNull SourcePos sourcePos) {
    var ctxTele = extract();
    var meta = Meta.from(ctxTele, type, sourcePos);
    var ref = new HoleVar<>(name, meta);
    var hole = new CallTerm.Hole(ref, ctxTele.map(Term.Param::toArg), meta.telescope.map(Term.Param::toArg));
    return Tuple2.of(meta, IntroTerm.Lambda.make(meta.telescope, hole));
  }

  public <T> T with(@NotNull Term.Param param, @NotNull Supplier<T> action) {
    return with(param.ref(), param.type(), action);
  }

  public <T> T with(@NotNull ImmutableSeq<Term.Param> params, @NotNull Supplier<T> action) {
    for (var param : params) localMap.put(param.ref(), param.type());
    try {
      return action.get();
    } finally {
      for (var param : params) localMap.remove(param.ref());
    }
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
    var ctx = Buffer.<Term.Param>of();
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

  public void put(@NotNull LocalVar var, @NotNull Term term) {
    localMap.set(var, term);
  }

  public boolean isEmpty() {
    return localMap.isEmpty() && (parent == null || parent.isEmpty());
  }

  @Contract(" -> new") public @NotNull LocalCtx derive() {
    return new LocalCtx(MutableMap.create(), this);
  }

  public boolean isNotEmpty() {
    return !isEmpty();
  }
}
