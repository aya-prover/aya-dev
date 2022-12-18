// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.stmt;

import kala.collection.Map;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Tuple;
import org.aya.util.binop.Assoc;
import org.aya.util.error.SourceNode;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr
 */
public record UseHide(@NotNull ImmutableSeq<@NotNull Name> list, @NotNull Strategy strategy) {
  public static final UseHide EMPTY = new UseHide(ImmutableSeq.empty(), Strategy.Hiding);

  public boolean uses(@NotNull String name) {
    return switch (strategy) {
      case Using -> list.anyMatch(n -> n.id.equals(name));
      case Hiding -> list.noneMatch(n -> n.id.equals(name));
    };
  }

  public @NotNull Map<String, String> renaming() {
    if (strategy == Strategy.Hiding) return ImmutableMap.empty();
    return list.view().map(i -> Tuple.of(i.id, i.asName)).toImmutableMap();
  }

  public @NotNull ImmutableSeq<String> listIds() {
    return list().map(Name::id);
  }

  /**
   * @author re-xyr
   */
  public enum Strategy {
    Using,
    Hiding,
  }

  public record Name(
    @NotNull SourcePos sourcePos,
    @NotNull String id,
    @NotNull String asName,
    @NotNull Assoc asAssoc,
    @NotNull BindBlock asBind
  ) implements SourceNode {
    public Name(@NotNull WithPos<@NotNull String> simple) {
      this(simple.sourcePos(), simple.data(), simple.data(), Assoc.Invalid, BindBlock.EMPTY);
    }
  }
}
