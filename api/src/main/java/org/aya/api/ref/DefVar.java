// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.api.ref;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.concrete.ConcreteDecl;
import org.aya.api.core.CoreDef;
import org.aya.util.binop.OpDecl;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

/**
 * @author ice1000
 */
public final class DefVar<Core extends CoreDef, Concrete extends ConcreteDecl> implements Var {
  /** Initialized in parsing, so it might be null for deserialized user definitions. */
  public @UnknownNullability Concrete concrete;
  /** Initialized in type checking or core deserialization, so it might be null for unchecked user definitions. */
  public @UnknownNullability Core core;
  /** Initialized in the resolver or core deserialization */
  public @UnknownNullability ImmutableSeq<String> module;
  private final @NotNull String name;
  public @UnknownNullability OpDecl opDecl;

  @Contract(pure = true)  public boolean isInfix() {
    return opDecl!=null && opDecl.opInfo() == null;
  }

  @Contract(pure = true) public @NotNull String name() {
    return name;
  }

  private DefVar(Concrete concrete, Core core, @NotNull String name) {
    this.concrete = concrete;
    this.core = core;
    this.name = name;
  }

  /** Used in user definitions. */
  public static <Core extends CoreDef, Concrete extends ConcreteDecl>
  @NotNull DefVar<Core, Concrete> concrete(@NotNull Concrete concrete, @NotNull String name) {
    return new DefVar<>(concrete, null, name);
  }

  /** Used in the serialization of core and primitive definitions. */
  public static <Core extends CoreDef, Concrete extends ConcreteDecl>
  @NotNull DefVar<Core, Concrete> empty(@NotNull String name) {
    return new DefVar<>(null, null, name);
  }

  @Override public boolean equals(Object o) {
    return this == o;
  }
}
