// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.ref;

import org.aya.syntax.concrete.stmt.decl.Decl;
import org.aya.syntax.core.def.Signature;
import org.aya.syntax.core.def.TyckDef;
import org.aya.util.binop.Assoc;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

public final class DefVar<Core extends TyckDef, Concrete extends Decl> implements AnyVar {
  private final @NotNull String name;
  /** Initialized in parsing. */
  public @NotNull Concrete concrete;
  public @Nullable Signature signature;
  /** Initialized in type checking, so it might be null for unchecked user definitions. */
  public @UnknownNullability Core core;
  /** Initialized in the resolver or core deserialization */
  public @Nullable QPath module;

  @Contract(pure = true) public @Nullable Assoc assoc() {
    if (concrete.opInfo() == null) return null;
    return concrete.opInfo().assoc();
  }

  @Contract(pure = true) public @NotNull String name() { return name; }
  private DefVar(@NotNull Concrete concrete, Core core, @NotNull String name) {
    this.concrete = concrete;
    this.core = core;
    this.name = name;
  }

  /** Used in user definitions. */
  public static <Core extends TyckDef, Concrete extends Decl>
  @NotNull DefVar<Core, Concrete> concrete(@NotNull Concrete concrete, @NotNull String name) {
    return new DefVar<>(concrete, null, name);
  }

  @Override public boolean equals(@Nullable Object o) {return this == o;}
  @Override public int hashCode() {return System.identityHashCode(this);}

  public boolean isInModule(@NotNull ModulePath moduleName) {
    return module != null && module.module().isInModule(moduleName);
  }
}
