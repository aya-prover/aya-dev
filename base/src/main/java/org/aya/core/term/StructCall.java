// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.def.StructDef;
import org.aya.util.Arg;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * @author kiva
 */
public record StructCall(
  @Override @NotNull DefVar<StructDef, TeleDecl.StructDecl> ref,
  @Override int ulift,
  @Override @NotNull ImmutableSeq<Arg<@NotNull Term>> args
) implements Callable.DefCall, StableWHNF, Formation {
  public @NotNull StructCall update(@NotNull ImmutableSeq<Arg<Term>> args) {
    return args.sameElements(args(), true) ? this : new StructCall(ref(), ulift(), args);
  }

  @Override public @NotNull StructCall descent(@NotNull UnaryOperator<@NotNull Term> f) {
    return update(args.map(arg -> arg.descent(f)));
  }
}
