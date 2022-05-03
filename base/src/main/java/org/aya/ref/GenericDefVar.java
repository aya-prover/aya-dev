// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ref;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.util.binop.OpDecl;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

/**
 * @author zaoqi
 */
public sealed abstract class GenericDefVar<Core, Concrete> implements Var permits ClassDefVar, DefVar {
  /** Initialized in parsing, so it might be null for deserialized user definitions. */
  public @UnknownNullability Concrete concrete;
  /** Initialized in type checking or core deserialization, so it might be null for unchecked user definitions. */
  public @UnknownNullability Core core;

  protected final @NotNull String name;

  /** Initialized in the resolver or core deserialization */
  public @Nullable ImmutableSeq<String> module;
  /** Initialized in the resolver or core deserialization */
  public @Nullable OpDecl opDecl;
  /** Initialized in the resolver or core deserialization */
  public @NotNull MutableMap<ImmutableSeq<String>, OpDecl> opDeclRename = MutableMap.create();

  protected GenericDefVar(@NotNull String name) {this.name = name;}

  @Contract(pure = true) public @NotNull String name() {
    return name;
  }

  @Contract(pure = true) public boolean isInfix() {
    return opDecl != null && opDecl.opInfo() != null;
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
