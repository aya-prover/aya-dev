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
public record Sort(@NotNull CoreLevel uLevel) {
  public Sort(@NotNull Level<LvlVar> uLevel) {
    this(new CoreLevel(uLevel));
  }

  public static final @NotNull Level<LvlVar> INF_LVL = new Level.Infinity<>();
  public static final @NotNull Sort OMEGA = new Sort(INF_LVL);

  private static @Nullable SourcePos unsolvedPos(@NotNull Level<LvlVar> lvl) {
    return lvl instanceof Level.Reference<LvlVar> ref ? ref.ref().pos : null;
  }

  public static @Nullable SourcePos unsolvedPos(@NotNull CoreLevel lvl) {
    return lvl.levels().view().mapNotNull(Sort::unsolvedPos).firstOrNull();
  }

  public @Nullable SourcePos unsolvedPos() {
    return unsolvedPos(uLevel);
  }

  public @NotNull Sort subst(@NotNull LevelSubst subst) {
    var u = subst.applyTo(uLevel);
    return u == uLevel ? this : new Sort(u);
  }

  public @NotNull Sort max(@NotNull Sort other) {
    return new Sort(max(uLevel, other.uLevel));
  }

  public static @NotNull CoreLevel max(@NotNull CoreLevel lhs, @NotNull CoreLevel rhs) {
    return new CoreLevel(lhs.levels().appendedAll(rhs.levels()));
  }

  /**
   * Lift the predicative universe level.
   */
  @Contract("_-> new") public @NotNull Sort succ(int n) {
    return new Sort(uLevel.lift(n));
  }

  /**
   * @param pos <code>null</code> if this is a bound level var, otherwise it represents the place
   *            where it gets generated and the level needs to be solved.
   *            In well-typed terms it should always be <code>null</code>.
   * @author ice1000
   */
  public static record LvlVar(@NotNull String name, @Nullable SourcePos pos) implements Var {
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

  /**
   * @param levels nonempty
   * @author ice1000
   */
  public static record CoreLevel(
    @NotNull ImmutableSeq<Level<LvlVar>> levels
  ) implements AyaDocile {
    public CoreLevel(@NotNull Level<LvlVar> level) {
      this(ImmutableSeq.of(level));
    }

    public static @NotNull CoreLevel merge(@NotNull ImmutableSeq<CoreLevel> levels) {
      if (levels.sizeEquals(1)) return levels.first();
      return new CoreLevel(levels.flatMap(CoreLevel::levels));
    }

    public @NotNull CoreLevel lift(int n) {
      return new CoreLevel(levels.map(l -> l.lift(n)));
    }

    @Override public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      return levels.sizeEquals(1) ? levels.first().toDoc(options) : Doc.sep(
        Doc.styled(CoreDistiller.KEYWORD, "lmax"),
        Doc.sep(levels.map(l -> l.toDoc(options)))
      );
    }
  }
}
