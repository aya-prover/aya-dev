// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.MapLike;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.collection.mutable.MutableMap;
import org.aya.resolve.context.ModuleContext;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.ClassDefLike;
import org.aya.syntax.core.def.MemberDefLike;
import org.aya.syntax.core.term.call.ClassCall;
import org.aya.syntax.ref.AnyDefVar;
import org.aya.syntax.ref.AnyVar;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.tycker.Stateful;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ClassResolver {
  /**
   * The module context we are in, it is considered immutable during the lifetime of this resolver.
   */
  public final @NotNull ModuleContext env;
  public @Nullable ImmutableMap<ClassDefLike, ImmutableSeq<AnyDefVar>> envCache;

  public ClassResolver(@NotNull ModuleContext env) {
    this.env = env;
  }

  public @NotNull ImmutableMap<ClassDefLike, ImmutableSeq<AnyDefVar>> getEnvClassInstance() {
    if (envCache != null) return envCache;
    var result = env.symbols().table()
      .valuesView()
      .flatMap(MapLike::valuesView)
      .filterIsInstance(AnyDefVar.class);

    var builder = MutableMap.<ClassDefLike, ImmutableSeq<AnyDefVar>>create();
    throw new UnsupportedOperationException("TODO");

    // TODO: we cannot use the instance that is defined in the same file,
    //  cause they are tycked in the same cycle, which might:
    //  * Add unexpected dependencies
    //  * May not have core

    // return envCache;
  }

  public void resolve(
    @NotNull MemberDefLike field,
    @NotNull ImmutableSeq<Jdg> args,
    @NotNull LocalCtx ctx,
    @NotNull Stateful normalizerProvider
  ) {
    var candies = findCandidates(field, ctx, normalizerProvider);
    var matches = MutableList.<AnyVar>create();


  }

  /**
   * Find the candidate for the invocation of {@code field args}
   *
   * @return the candidates, either {@link AnyDefVar} or {@link LocalVar}
   */
  public @NotNull ImmutableSeq<AnyVar> findCandidates(
    @NotNull MemberDefLike field,
    @NotNull LocalCtx ctx,
    @NotNull Stateful normalizerProvider
  ) {
    var candies = MutableList.<AnyVar>create();
    // find from localCtx
    // can be improved by forEach and extractLocal (if there is)
    ctx.extract().forEach(v -> {
      if (normalizerProvider.whnf(ctx.get(v)) instanceof ClassCall call && field.classRef().equals(call.ref())) {
        candies.append(AnyDef.toVar(call.ref()));
      }
    });

    // find from environment
    getEnvClassInstance().getOption(field.classRef())
      .forEach(candies::appendAll);

    return candies.toImmutableSeq();
  }
}
