// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LevelGenVar;
import org.aya.api.ref.Var;
import org.aya.core.visitor.CoreDistiller;
import org.aya.generic.Level;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
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
public record Sort(@NotNull CoreLevel uLevel, @NotNull CoreLevel hLevel) {
  public Sort(@NotNull Level<LvlVar> uLevel, @NotNull Level<LvlVar> hLevel) {
    this(new CoreLevel(uLevel), new CoreLevel(hLevel));
  }

  public static final @NotNull Level<LvlVar> INF_LVL = new Level.Infinity<>();
  public static final @NotNull Sort OMEGA = new Sort(INF_LVL, INF_LVL);

  public @Nullable Level<LvlVar> onlyH() {
    if (hLevel.levels.sizeEquals(1)) return hLevel.levels.first();
    else return null;
  }

  private static @Nullable SourcePos unsolvedPos(@NotNull Level<LvlVar> lvl) {
    return lvl instanceof Level.Reference<LvlVar> ref ? ref.ref().pos : null;
  }

  public static @Nullable SourcePos unsolvedPos(@NotNull CoreLevel lvl) {
    return lvl.levels().view().mapNotNull(Sort::unsolvedPos).firstOrNull();
  }

  public @Nullable SourcePos unsolvedPos() {
    var pos = unsolvedPos(uLevel);
    return pos != null ? pos : unsolvedPos(hLevel);
  }

  public @NotNull Sort subst(@NotNull LevelSubst subst) {
    var u = subst.applyTo(uLevel);
    var h = subst.applyTo(hLevel);
    return u == uLevel && h == hLevel ? this : new Sort(u, h);
  }

  public @NotNull Sort max(@NotNull Sort other) {
    return new Sort(max(uLevel, other.uLevel), max(hLevel, other.hLevel));
  }

  public static @NotNull CoreLevel max(@NotNull CoreLevel lhs, @NotNull CoreLevel rhs) {
    return new CoreLevel(lhs.levels().appendedAll(rhs.levels()));
  }

  @Contract("_-> new") public @NotNull Sort succ(int n) {
    return new Sort(uLevel.lift(n), hLevel.lift(n));
  }

  /**
   * @param pos <code>null</code> if this is a bound level var, otherwise it represents the place
   *            where it gets generated and the level needs to be solved.
   *            In well-typed terms it should always be <code>null</code>.
   * @author ice1000
   */
  public static record LvlVar(
    @NotNull String name,
    @NotNull LevelGenVar.Kind kind,
    @Nullable SourcePos pos
  ) implements Var {
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
  ) implements Docile {
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

    @Override public @NotNull Doc toDoc() {
      return levels.sizeEquals(1) ? levels.first().toDoc() : Doc.sep(
        Doc.styled(CoreDistiller.KEYWORD, "lmax"),
        Doc.sep(levels.map(Docile::toDoc))
      );
    }
  }
}
