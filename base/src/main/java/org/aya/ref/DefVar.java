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
public final class DefVar<Core extends Def, Concrete extends Signatured> implements Var {
  private final @NotNull String name;
  /** Initialized in parsing, so it might be null for deserialized user definitions. */
  public @UnknownNullability Concrete concrete;
  /** Initialized in type checking or core deserialization, so it might be null for unchecked user definitions. */
  public @UnknownNullability Core core;
  /** Initialized in the resolver or core deserialization */
  public @Nullable ImmutableSeq<String> module;
  /** Initialized in the resolver or core deserialization */
  public @Nullable OpDecl opDecl;
  /** Initialized in the resolver or core deserialization */
  public @NotNull MutableMap<ImmutableSeq<String>, OpDecl> opDeclRename = MutableMap.create();


  @Contract(pure = true) public boolean isInfix() {
    return opDecl != null && opDecl.opInfo() != null;
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
  public static <Core extends Def, Concrete extends Signatured>
  @NotNull DefVar<Core, Concrete> concrete(@NotNull Concrete concrete, @NotNull String name) {
    return new DefVar<>(concrete, null, name);
  }

  /** Used in the serialization of core and primitive definitions. */
  public static <Core extends Def, Concrete extends Signatured>
  @NotNull DefVar<Core, Concrete> empty(@NotNull String name) {
    return new DefVar<>(null, null, name);
  }

  @Override public boolean equals(Object o) {
    return this == o;
  }

  public boolean isInModule(@NotNull ImmutableSeq<String> moduleName) {
    var maybeSubmodule = module;
    if (maybeSubmodule == null) return false;
    if (maybeSubmodule.sizeLessThan(moduleName.size())) return false;
    return maybeSubmodule.sliceView(0, moduleName.size()).sameElements(moduleName);
  }
}
