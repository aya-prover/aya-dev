// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.api.ref;

import org.aya.api.concrete.ConcreteDecl;
import org.aya.api.core.CoreDef;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

/**
 * @author ice1000
 */
public final class DefVar<Core extends CoreDef, Concrete extends ConcreteDecl> implements Var {
  public @UnknownNullability Concrete concrete;
  public @UnknownNullability Core core;
  private final @NotNull String name;

  @Contract(pure = true) public @NotNull String name() {
    return name;
  }

  private DefVar(Concrete concrete, Core core, @NotNull String name) {
    this.concrete = concrete;
    this.core = core;
    this.name = name;
  }

  public static <Core extends CoreDef, Concrete extends ConcreteDecl> @NotNull DefVar<Core, Concrete> concrete(@NotNull Concrete concrete, @NotNull String name) {
    return new DefVar<>(concrete, null, name);
  }

  public static <Core extends CoreDef, Concrete extends ConcreteDecl> @NotNull DefVar<Core, Concrete> core(Core core, @NotNull String name) {
    return new DefVar<>(null, core, name);
  }

  @Override public boolean equals(Object o) {
    return this == o;
  }
}
