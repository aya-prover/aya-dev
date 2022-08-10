// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.Map;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import org.aya.util.binop.Assoc;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public record UseHide(@NotNull ImmutableSeq<@NotNull Name> list, @NotNull Strategy strategy) {
  public static final UseHide EMPTY = new UseHide(ImmutableSeq.empty(), Strategy.Hiding);

  public boolean uses(@NotNull String name) {
    return switch (strategy) {
      case Using -> list.find(n -> n.id.equals(name)).isDefined();
      case Hiding -> list.find(n -> n.id.equals(name)).isEmpty();
    };
  }

  public @NotNull Map<String, String> renaming() {
    if (strategy == Strategy.Hiding) return ImmutableMap.empty();
    return list.view().map(i -> Tuple.of(i.id, i.asName)).toImmutableMap();
  }

  /**
   * @author re-xyr
   */
  public enum Strategy {
    Using,
    Hiding,
  }

  public record Name(
    @NotNull String id,
    @NotNull String asName,
    @NotNull Assoc asAssoc,
    @NotNull BindBlock asBind
  ) {
  }
}
