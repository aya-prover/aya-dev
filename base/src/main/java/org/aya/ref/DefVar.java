// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ref;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.concrete.stmt.Signatured;
import org.aya.core.def.Def;
import org.aya.util.binop.OpDecl;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

/**
 * @author ice1000
 */
public final class DefVar<Core extends Def, Concrete extends Signatured> extends GenericDefVar<Core, Concrete> {
  private DefVar(Concrete concrete, Core core, @NotNull String name) {
    super(name);
    this.concrete = concrete;
    this.core = core;
  }

  /** Used in user definitions. */
  public static <Core extends Def, Concrete extends Signatured>
  @NotNull DefVar<Core, Concrete> concrete(@NotNull Concrete concrete, @NotNull String name) {
    return new DefVar<>(concrete, null, name);
  }

  /** Used in the serialization of core and primitive definitions. */
  public static <Core extends Def, Concrete extends Signatured>
  @NotNull DefVar<Core, Concrete> empty(@NotNull String name) {
    return new DefVar<>(null, null, name);
  }
}
