// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ref;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.ClassDecl;
import org.aya.core.def.ClassDef;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

public final class ClassDefVar<Core extends ClassDef, Concrete extends ClassDecl> extends GenericDefVar<Core, Concrete> {
  private ClassDefVar(Concrete concrete, Core core, @NotNull String name) {
    super(name);
    this.concrete = concrete;
    this.core = core;
  }

  /** Used in user definitions. */
  public static <Core extends ClassDef, Concrete extends ClassDecl>
  @NotNull ClassDefVar<Core, Concrete> concrete(@NotNull Concrete concrete, @NotNull String name) {
    return new ClassDefVar<>(concrete, null, name);
  }

  /** Used in the serialization of core and primitive definitions. */
  public static <Core extends ClassDef, Concrete extends ClassDecl>
  @NotNull ClassDefVar<Core, Concrete> empty(@NotNull String name) {
    return new ClassDefVar<>(null, null, name);
  }
}
