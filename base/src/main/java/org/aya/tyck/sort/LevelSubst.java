// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.sort;

import org.aya.api.ref.Var;
import org.aya.tyck.sort.Sort.Level;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * References in Arend:
 * <a href="https://github.com/JetBrains/Arend/blob/master/base/src/main/java/org/arend/core/subst/StdLevelSubstitution.java"
 * >StdLevelSubstitution.java</a> (as {@link Sort}),
 * <a href="https://github.com/JetBrains/Arend/blob/master/base/src/main/java/org/arend/core/subst/SimpleLevelSubstitution.java"
 * >SimpleLevelSubstitution.java</a>, etc.
 */
public interface LevelSubst {
  boolean isEmpty();
  @Nullable Level get(@NotNull Var var);
  @NotNull LevelSubst subst(@NotNull LevelSubst subst);
  @NotNull LevelSubst EMPTY = new Simple(MutableHashMap.of());

  record Simple(@NotNull MutableMap<@NotNull Var, @NotNull Level> map) implements LevelSubst {
    public void add(@NotNull Var var, @NotNull Level level) {
      map.put(var, level);
    }

    @Override public boolean isEmpty() {
      return map.isEmpty();
    }

    @Override public @Nullable Level get(@NotNull Var var) {
      return map.getOrNull(var);
    }

    @Override public @NotNull LevelSubst subst(@NotNull LevelSubst subst) {
      if (isEmpty()) return this;
      var result = new Simple(new MutableHashMap<>());
      map.replaceAll((var, term) -> term.subst(subst));
      return result;
    }
  }
}
