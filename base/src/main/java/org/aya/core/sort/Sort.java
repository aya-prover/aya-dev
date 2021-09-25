// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.distill.CoreDistiller;
import org.aya.generic.Level;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Not inspired from Arend.
 * <a href="https://github.com/JetBrains/Arend/blob/master/base/src/main/java/org/arend/core/sort/Sort.java"
 * >Sort.java</a>
 *
 * @author ice1000
 */
public record Sort(@NotNull ImmutableSeq<Level<LvlVar>> levels) implements AyaDocile {
  public Sort(@NotNull Level<LvlVar> level) {
    this(ImmutableSeq.of(level));
  }

  public static final @NotNull Level<LvlVar> INF_LVL = new Level.Infinity<>();
  public static final @NotNull Sort OMEGA = new Sort(INF_LVL);

  private static @Nullable SourcePos unsolvedPos(@NotNull Level<LvlVar> lvl) {
    return lvl instanceof Level.Reference<LvlVar> ref ? ref.ref().pos : null;
  }

  public @Nullable SourcePos unsolvedPos() {
    return this.levels().view().mapNotNull(Sort::unsolvedPos).firstOrNull();
  }

  public static @NotNull Sort max(@NotNull Sort lhs, @NotNull Sort rhs) {
    return new Sort(lhs.levels().appendedAll(rhs.levels()));
  }

  public static @NotNull Sort merge(@NotNull ImmutableSeq<Sort> levels) {
    if (levels.sizeEquals(1)) return levels.first();
    return new Sort(levels.flatMap(Sort::levels));
  }

  public @NotNull Sort lift(int n) {
    return new Sort(levels.map(l -> l.lift(n)));
  }

  @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
    return levels.sizeEquals(1) ? levels.first().toDoc(options) : Doc.parened(Doc.sep(
      Doc.styled(CoreDistiller.KEYWORD, "lmax"),
      Doc.sep(levels.map(l -> l.toDoc(options)))
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

    public boolean free() {
      return pos != null;
    }
  }
}
