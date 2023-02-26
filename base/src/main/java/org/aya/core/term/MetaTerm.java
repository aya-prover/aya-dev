// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.core.meta.Meta;
import org.aya.core.pat.Pat;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * @author ice1000
 */
public record MetaTerm(
  @NotNull Meta ref,
  @NotNull ImmutableSeq<@NotNull Arg<@NotNull Term>> contextArgs,
  @NotNull ImmutableSeq<@NotNull Arg<@NotNull Term>> args
) implements Callable {
  public MetaTerm {

  }

  public @NotNull MetaTerm update(@NotNull ImmutableSeq<Arg<Term>> contextArgs, @NotNull ImmutableSeq<Arg<Term>> args) {
    return contextArgs.sameElements(contextArgs(), true) && args.sameElements(args(), true) ? this
      : new MetaTerm(ref, contextArgs, args);
  }

  @Override public @NotNull MetaTerm descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(contextArgs.map(arg -> arg.descent(f)), args.map(arg -> arg.descent(f)));
  }

  public @NotNull PiTerm asPi(boolean explicit) {
    return ref.asPi(ref.name() + "dom", ref.name() + "cod", explicit, contextArgs);
  }

  public @NotNull MetaTerm asPiDom(@NotNull SortTerm result) {
    return ref.asPiDom(result, contextArgs);
  }

  public @NotNull SeqView<@NotNull Arg<Term>> fullArgs() {
    return contextArgs.view().concat(args);
  }

}
