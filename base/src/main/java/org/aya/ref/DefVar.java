// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.ref;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.concrete.stmt.decl.Decl;
import org.aya.core.def.Def;
import org.aya.core.def.GenericDef;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.ModulePath;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.OpDecl;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

/**
 * @author ice1000
 */
public final class DefVar<Core extends GenericDef, Concrete extends Decl> implements AnyVar {
  private final @NotNull String name;
  /** Initialized in parsing, so it might be null for deserialized user definitions. */
  public @UnknownNullability Concrete concrete;
  /** Initialized in type checking or core deserialization, so it might be null for unchecked user definitions. */
  public @UnknownNullability Core core;
  /** Initialized in the resolver or core deserialization */
  public @Nullable ImmutableSeq<String> module;
  /** Initialized in the resolver or core deserialization */
  public @Nullable OpDecl opDecl;
  /**
   * Binary operators can be renamed in other modules.
   * Initialized in the resolver or core deserialization.
   * see {@link ResolveInfo#opRename()}
   */
  public @NotNull MutableMap<ModulePath, OpDecl> opDeclRename = MutableMap.create();

  @Contract(pure = true) public @Nullable Assoc assoc() {
    if (opDecl == null) return null;
    if (opDecl.opInfo() == null) return null;
    return opDecl.opInfo().assoc();
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
  public static <Core extends GenericDef, Concrete extends Decl>
  @NotNull DefVar<Core, Concrete> concrete(@NotNull Concrete concrete, @NotNull String name) {
    return new DefVar<>(concrete, null, name);
  }

  /** Used in the serialization of core and primitive definitions. */
  public static <Core extends Def, Concrete extends Decl>
  @NotNull DefVar<Core, Concrete> empty(@NotNull String name) {
    return new DefVar<>(null, null, name);
  }

  public @Nullable OpDecl resolveOpDecl(@NotNull ModulePath modulePath) {
    return opDeclRename.getOrDefault(modulePath, opDecl);
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

  public @NotNull ImmutableSeq<String> qualifiedName() {
    return module == null ? ImmutableSeq.of(name) : module.appended(name);
  }
}
