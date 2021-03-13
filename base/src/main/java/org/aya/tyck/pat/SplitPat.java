// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.core.pat.Pat;
import org.aya.generic.Arg;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.jetbrains.annotations.NotNull;

public final class SplitPat implements Pat.Visitor<Integer, SplitPat.Tree> {
  public static final @NotNull SplitPat INSTANCE = new SplitPat();

  private SplitPat() {
  }

  @Override public Tree visitBind(Pat.@NotNull Bind bind, Integer i) {
    return Tree.Done.INSTANCE;
  }

  @Override public Tree visitTuple(Pat.@NotNull Tuple tuple, Integer i) {
    throw new UnsupportedOperationException();
  }

  @Override public Tree visitCtor(Pat.@NotNull Ctor ctor, Integer i) {
    return new Tree.Con(new Arg<>(i, ctor.explicit()),
      ctor.params().mapIndexed((j, p) -> p.accept(this, j)));
  }

  /**
   * @author ice1000
   */
  public sealed interface Tree {
    final class Done implements Tree {
      public static final @NotNull Done INSTANCE = new Done();

      private Done() {
      }
    }

    /**
     * @param on the n-th parameter to split on
     */
    record Con(
      @NotNull Arg<@NotNull Integer> on,
      @NotNull ImmutableSeq<Tree> children
    ) implements Tree {
    }
  }
}
