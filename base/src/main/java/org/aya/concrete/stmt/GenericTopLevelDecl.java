// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import org.aya.core.def.GenericDef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;

/**
 * Generic concrete definition, corresponding to {@link GenericDef}.
 *
 * @author zaoqi
 */
public sealed interface GenericTopLevelDecl extends GenericDecl permits ClassDecl, Decl {
  enum Personality {
    NORMAL,
    EXAMPLE,
    COUNTEREXAMPLE,
  }

  @NotNull Personality personality();

  interface Visitor<P, R> {
    default void traceEntrance(@NotNull Object item, P p) {
    }
    default void traceExit(P p, R r) {
    }

    @ApiStatus.NonExtendable
    default <T, RR extends R> RR traced(@NotNull T yeah, P p, @NotNull BiFunction<T, P, RR> f) {
      traceEntrance(yeah, p);
      var r = f.apply(yeah, p);
      traceExit(p, r);
      return r;
    }
  }
}
