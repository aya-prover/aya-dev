// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.ModulePath;
import org.aya.syntax.ref.QName;
import org.aya.util.binop.Assoc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public non-sealed class TyckAnyDef<Interface extends TyckDef> implements AnyDef {
  public final @NotNull DefVar<Interface , ?> ref;
  public Interface core() { return ref.core; }
  public TyckAnyDef(@NotNull DefVar<Interface, ?> ref) { this.ref = ref; }
  @Override public final @NotNull ModulePath fileModule() { return Objects.requireNonNull(ref.module).fileModule(); }
  @Override public final @NotNull ModulePath module() { return Objects.requireNonNull(ref.module).module(); }
  @Override public final @NotNull String name() { return ref.name(); }
  @Override public final @Nullable Assoc assoc() { return ref.assoc(); }
  @Override
  public @NotNull QName qualifiedName() { return new QName(ref); }
  @Override public boolean equals(Object obj) {
    return obj instanceof TyckAnyDef<?> that && ref.equals(that.ref);
  }
  @Override public int hashCode() { return ref.hashCode(); }

  public static TyckAnyDef<?> make(TyckDef core) {
    return switch (core) {
      case DataDef data -> new DataDef.Delegate(data.ref);
      case FnDef fn -> new FnDef.Delegate(fn.ref());
      case PrimDef prim -> new PrimDef.Delegate(prim.ref);
      case ConDef con -> new ConDef.Delegate(con.ref);
    };
  }
  @Override public @Nullable OpInfo opInfo() { return ref.concrete.opInfo(); }
}
