// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.util.binop.Assoc;
import org.aya.util.position.SourceNode;
import org.aya.util.position.SourcePos;
import org.aya.util.position.WithPos;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * @author re-xyr
 */
public record UseHide(@NotNull ImmutableSeq<@NotNull Name> list, @NotNull Strategy strategy) {
  public static final UseHide EMPTY = new UseHide(ImmutableSeq.empty(), Strategy.Hiding);

  public record Rename(@NotNull QualifiedID name,
                       @NotNull String to) implements Serializable {}

  public @NotNull ImmutableSeq<WithPos<Rename>> renaming() {
    if (strategy == Strategy.Hiding) return ImmutableSeq.empty();
    return list.flatMap(i -> i.rename().map(x -> new WithPos<>(i.sourcePos(), x)));
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
    @NotNull QualifiedID id,
    @NotNull Option<String> asName,
    @NotNull Assoc asAssoc,
    @NotNull BindBlock asBind
  ) implements SourceNode {
    public Name(@NotNull QualifiedID name) {
      this(name.sourcePos(), name, Option.none(), Assoc.Unspecified, BindBlock.EMPTY);
    }

    public Option<Rename> rename() {
      return asName.map(x -> new Rename(id, x));
    }
  }
}
