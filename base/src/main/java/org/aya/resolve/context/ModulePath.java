// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.Constants;
import org.aya.generic.util.InternalException;
import org.jetbrains.annotations.NotNull;

public sealed interface ModulePath {
  final class This implements ModulePath {
    private This() {
    }

    @Override public @NotNull ImmutableSeq<String> ids() {
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

    @Override public @NotNull Qualified resolve(@NotNull String name) {
      return new Qualified(ids.appended(name));
    }

    @Override public @NotNull Qualified concat(@NotNull ModulePath path) {
      return new Qualified(ids.concat(path.ids()));
    }

    @Override public @NotNull String toString() {
      return ids.joinToString(Constants.SCOPE_SEPARATOR);
    }
  }

  @NotNull ImmutableSeq<String> ids();
  @NotNull Qualified resolve(@NotNull String name);
  @NotNull ModulePath concat(@NotNull ModulePath path);
  @NotNull String toString();

  /// region static

  @NotNull This This = new This();

  static @NotNull ModulePath from(@NotNull ImmutableSeq<String> ids) {
    if (ids.isEmpty()) return This;
    return new Qualified(ids);
  }

  /**
   * Construct a qualified module path from a not empty id sequence.
   *
   * @param ids a not empty sequence
   */
  static @NotNull ModulePath.Qualified qualified(@NotNull ImmutableSeq<String> ids) {
    if (ids.isEmpty()) throw new InternalException("A valid module path cannot be empty");
    return new Qualified(ids);
  }

  /// endregion
}
