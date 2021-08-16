// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.serde;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.api.ref.LevelGenVar;
import org.aya.core.sort.Sort;
import org.aya.generic.Level;
import org.aya.util.Constants;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * @author ice1000
 */
public sealed interface SerLevel extends Serializable {
  @NotNull Level<Sort.LvlVar> de(@NotNull MutableMap<Long, Sort.LvlVar> cache);

  /** @param num -1 means infinity */
  record Const(int num) implements SerLevel {
    @Override public @NotNull Level<Sort.LvlVar> de(@NotNull MutableMap<Long, Sort.LvlVar> cache) {
      return num >= 0 ? new Level.Constant<>(num) : new Level.Infinity<>();
    }
  }

  record Ref(long id, @NotNull LevelGenVar.Kind kind, int lift) implements SerLevel {
    @Override public @NotNull Level<Sort.LvlVar> de(@NotNull MutableMap<Long, Sort.LvlVar> cache) {
      return new Level.Reference<>(cache.getOrPut(id, () ->
        new Sort.LvlVar(Constants.ANONYMOUS_PREFIX, kind, null)));
    }
  }

  record Max(@NotNull ArrayList<SerLevel> levels) implements Serializable {
    public @NotNull Sort.CoreLevel de(@NotNull MutableMap<Long, Sort.LvlVar> cache) {
      return new Sort.CoreLevel(ImmutableSeq.from(levels.stream().map(l -> l.de(cache))));
    }
  }
}
