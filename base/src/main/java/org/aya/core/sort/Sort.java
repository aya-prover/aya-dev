// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.sort;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.ref.Var;
import org.aya.distill.CoreDistiller;
import org.aya.generic.Level;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Not inspired from Arend.
 * <a href="https://github.com/JetBrains/Arend/blob/master/base/src/main/java/org/arend/core/sort/Sort.java"
 * >Sort.java</a>
 *
 * @author ice1000
 */
public record Sort(@NotNull ImmutableSeq<Level<LvlVar>> levels) implements Docile {
  public Sort(@NotNull Level<LvlVar> level) {
    this(ImmutableSeq.of(level));
  }

  private static @Nullable SourcePos unsolvedPos(@NotNull Level<LvlVar> lvl) {
    return lvl instanceof Level.Reference<LvlVar> ref ? ref.ref().pos : null;
  }

  public @Nullable SourcePos unsolvedPos() {
    return this.levels().view().mapNotNull(Sort::unsolvedPos).firstOrNull();
  }

  public static @NotNull Sort merge(@NotNull ImmutableSeq<Sort> levels) {
    if (levels.sizeEquals(1)) return levels.first();
    return new Sort(levels.flatMap(Sort::levels));
  }

  public @NotNull Sort lift(int n) {
    return new Sort(levels.map(l -> l.lift(n)));
  }

  @Override public @NotNull Doc toDoc() {
    return levels.sizeEquals(1) ? levels.first().toDoc() : Doc.parened(Doc.sep(
      Doc.styled(CoreDistiller.KEYWORD, "lmax"),
      Doc.sep(levels.map(Docile::toDoc))
    ));
  }

  /**
   * @param pos <code>null</code> if this is a bound level var, otherwise it represents the place
   *            where it gets generated and the level needs to be solved.
   *            In well-typed terms it should always be <code>null</code>.
   * @author ice1000
   */
  public record LvlVar(@NotNull String name, @Nullable SourcePos pos) implements Var {
    @Override public boolean equals(@Nullable Object o) {
      return this == o;
    }

    @Override public int hashCode() {
      return System.identityHashCode(this);
    }

    @Contract(pure = true) public boolean free() {
      return pos != null;
    }
  }
}
