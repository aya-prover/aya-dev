// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.context;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.QualifiedID;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * An absolute path to a module.
 * <p/>
 * This is exactly a {@link ModuleName.Qualified}, but I think you don't want to use a local name as an absolute path by accident.
 *
 * @param path not empty path
 */
public record ModulePath(@NotNull ImmutableSeq<String> path) implements Serializable {
  public static @NotNull ModulePath of(@NotNull String... names) {
    return new ModulePath(ImmutableSeq.from(names));
  }

  public ModulePath {
    assert path.isNotEmpty() : "What's this?";
  }

  public @NotNull ModuleName.Qualified asName() {
    return ModuleName.qualified(path);
  }

  public @NotNull ModulePath derive(@NotNull String... names) {
    return new ModulePath(path.appendedAll(names));
  }

  public @NotNull ModulePath derive(@NotNull ImmutableSeq<String> names) {
    return new ModulePath(path.appendedAll(names));
  }

  @Override
  public String toString() {
    return QualifiedID.join(path);
  }
}
