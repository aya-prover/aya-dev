// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.Constants;
import org.jetbrains.annotations.NotNull;

public sealed interface ModulePath {
  final class This implements ModulePath {
    private This() {
    }

    @Override public @NotNull ImmutableSeq<String> toImmutableSeq() {
      return ImmutableSeq.empty();
    }

    @Override public @NotNull Qualified resolve(@NotNull String name) {
      return new Qualified(ImmutableSeq.of(name));
    }

    @Override public @NotNull ModulePath concat(@NotNull ModulePath path) {
      return path;
    }

    @Override public @NotNull String toString() {
      return "";
    }
  }

  record Qualified(@NotNull ImmutableSeq<String> ids) implements ModulePath {
    public Qualified {
      assert ids.isNotEmpty() : "Otherwise please use `This`";
    }

    @Override public @NotNull ImmutableSeq<String> toImmutableSeq() {
      return ids;
    }

    @Override public @NotNull Qualified resolve(@NotNull String name) {
      return new Qualified(ids.appended(name));
    }

    @Override public @NotNull Qualified concat(@NotNull ModulePath path) {
      return new Qualified(ids.concat(path.toImmutableSeq()));
    }

    @Override public @NotNull String toString() {
      return ids.joinToString(Constants.SCOPE_SEPARATOR);
    }
  }

  @NotNull ImmutableSeq<String> toImmutableSeq();
  @NotNull Qualified resolve(@NotNull String name);
  @NotNull ModulePath concat(@NotNull ModulePath path);
  @NotNull String toString();

  /// region static

  @NotNull This This = new This();

  static @NotNull ModulePath from(@NotNull SeqLike<String> ids) {
    if (ids.isEmpty()) return This;

    return new Qualified(ids.toImmutableSeq());
  }

  /**
   * Construct a qualified module path from a not empty id sequence.
   *
   * @param ids a not empty sequence
   */
  static @NotNull ModulePath.Qualified ofQualified(@NotNull SeqLike<String> ids) {
    if (ids.isEmpty()) throw new IndexOutOfBoundsException(0);
    return new Qualified(ids.toImmutableSeq());
  }

  /// endregion
}
