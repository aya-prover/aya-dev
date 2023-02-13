// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableMap;
import kala.tuple.Tuple;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.def.ClassDef;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * @author kiva
 */
public record NewTerm(
  @NotNull ClassCall struct,
  @NotNull ImmutableMap<DefVar<ClassDef.Member, TeleDecl.ClassMember>, Term> params
) implements StableWHNF {
  public @NotNull NewTerm update(@NotNull ClassCall struct, @NotNull ImmutableMap<DefVar<ClassDef.Member, TeleDecl.ClassMember>, Term> params) {
    var equalParams = params == params()
      || params.view().map(Tuple::of).sameElements(params().view().map(Tuple::of));
    return struct == struct() && equalParams ? this : new NewTerm(struct, params);
  }

  @Override public @NotNull NewTerm descent(@NotNull UnaryOperator<@NotNull Term> f) {
    return update((ClassCall) f.apply(struct), ImmutableMap.from(params.view().map((k, v) -> Tuple.of(k, f.apply(v)))));
  }
}
