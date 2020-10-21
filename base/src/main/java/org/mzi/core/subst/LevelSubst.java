package org.mzi.core.subst;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mzi.api.ref.Ref;
import org.mzi.core.sort.Sort;
import org.mzi.core.sort.Sort.Level;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * References in Arend:
 * <a href="https://github.com/JetBrains/Arend/blob/master/base/src/main/java/org/arend/core/subst/StdLevelSubstitution.java"
 * >StdLevelSubstitution.java</a> (as {@link Sort}),
 * <a href="https://github.com/JetBrains/Arend/blob/master/base/src/main/java/org/arend/core/subst/SimpleLevelSubstitution.java"
 * >SimpleLevelSubstitution.java</a>, etc.
 */
public interface LevelSubst {
  boolean isEmpty();
  @Nullable Level get(@NotNull Ref ref);
  @NotNull LevelSubst subst(@NotNull LevelSubst subst);
  @NotNull LevelSubst EMPTY = new Simple(Collections.emptyMap());

  class Simple implements LevelSubst {
    private final @NotNull Map<@NotNull Ref, @NotNull Level> map;

    public Simple(@NotNull Map<@NotNull Ref, @NotNull Level> map) {
      this.map = map;
    }

    public void add(@NotNull Ref ref, @NotNull Level level) {
      map.put(ref, level);
    }

    @Override public boolean isEmpty() {
      return map.isEmpty();
    }

    @Override public @Nullable Level get(@NotNull Ref ref) {
      return map.get(ref);
    }

    @Override public @NotNull LevelSubst subst(@NotNull LevelSubst subst) {
      if (isEmpty()) return this;
      var result = new Simple(new HashMap<>());
      for (var entry : map.entrySet()) result.map.put(entry.getKey(), entry.getValue().subst(subst));
      return result;
    }
  }
}
