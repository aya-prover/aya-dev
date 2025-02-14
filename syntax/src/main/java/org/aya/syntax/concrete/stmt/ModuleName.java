// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.concrete.stmt;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/// This interface represents a name to some module,
/// which can be a relative [ThisRef] or a qualified name [Qualified].
///
/// This name should be used in a local scope instead of a global scope (like a path to a module).
public sealed interface ModuleName extends Serializable {
  enum ThisRef implements ModuleName {
    Obj;

    @Override public @NotNull ImmutableSeq<String> ids() { return ImmutableSeq.empty(); }
    @Override public @NotNull Qualified resolve(@NotNull String name) {
      return new Qualified(ImmutableSeq.of(name));
    }
    @Override public @NotNull ModuleName concat(@NotNull ModuleName path) { return path; }
    @Override public @NotNull String toString() { return ""; }
  }

  record Qualified(@NotNull ImmutableSeq<String> ids) implements ModuleName {
    public Qualified {
      assert ids.isNotEmpty() : "Otherwise please use `This`";
    }

    @Override public @NotNull Qualified resolve(@NotNull String name) {
      return new Qualified(ids.appended(name));
    }

    @Override public @NotNull Qualified concat(@NotNull ModuleName path) {
      return new Qualified(ids.concat(path.ids()));
    }
    @Override public @NotNull String toString() { return QualifiedID.join(ids); }
  }

  @NotNull ImmutableSeq<String> ids();
  @NotNull Qualified resolve(@NotNull String name);
  @NotNull ModuleName concat(@NotNull ModuleName path);
  @NotNull String toString();

  // region static

  @NotNull ModuleName.ThisRef This = ThisRef.Obj;

  static @NotNull ModuleName from(@NotNull SeqLike<String> ids) {
    if (ids.isEmpty()) return This;
    return new Qualified(ids.toSeq());
  }

  static @NotNull ModuleName.ThisRef of() {
    return ModuleName.This;
  }

  static @NotNull ModuleName.Qualified of(@NotNull String first, @NotNull String @NotNull ... tail) {
    return new Qualified(ImmutableSeq.from(SeqView.of(first).concat(ImmutableArray.Unsafe.wrap(tail))));
  }

  /**
   * Construct a qualified module path from a not empty id sequence.
   *
   * @param ids a not empty sequence
   */
  static @NotNull ModuleName.Qualified qualified(@NotNull ImmutableSeq<String> ids) {
    if (ids.isEmpty()) throw new Panic("A valid module path cannot be empty");
    return new Qualified(ids);
  }

  // endregion
}
