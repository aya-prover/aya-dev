// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.core.Meta;
import org.aya.generic.Arg;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record MetaTerm(
  @NotNull Meta ref,
  // TODO[ice]: remove this below
  int ulift,
  @NotNull ImmutableSeq<@NotNull Arg<@NotNull Term>> contextArgs,
  @NotNull ImmutableSeq<@NotNull Arg<@NotNull Term>> args
) implements Callable {
  public @NotNull PiTerm asPi(boolean explicit) {
    return ref.asPi(ref.name() + "dom", ref.name() + "cod", explicit, ulift, contextArgs);
  }

  public @NotNull SeqView<@NotNull Arg<Term>> fullArgs() {
    return contextArgs.view().concat(args);
  }

}
