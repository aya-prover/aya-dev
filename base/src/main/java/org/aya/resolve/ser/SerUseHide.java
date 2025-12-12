// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.ser;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.ModuleContext;
import org.aya.syntax.concrete.stmt.BindBlock;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.Stmt;
import org.aya.syntax.concrete.stmt.UseHide;
import org.aya.util.binop.Assoc;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

/// @see UseHide
public record SerUseHide(
  @NotNull ImmutableSeq<SerName> names,
  boolean isUsing
) implements Serializable {
  public record SerName(
    @NotNull SerQualifiedID name,
    @Nullable String rename
  ) {
    /// @param name must be resolved
    public static @NotNull SerName from(@NotNull UseHide.Name name) {
      return new SerName(SerQualifiedID.from(name.id()), name.asName().getOrNull());
    }

    /// since `bind` is done in another pass, and we can bind imported symbol and definition in the same time,
    /// thus we can use [Assoc#Unspecified] to skip the bind pass by [org.aya.resolve.visitor.StmtPreResolver#resolveOpen]
    public @NotNull UseHide.Name toName() {
      return new UseHide.Name(
        SourcePos.SER, name.make(), Option.ofNullable(rename), Assoc.Unspecified, BindBlock.EMPTY
      );
    }
  }

  public static @NotNull SerUseHide from(@NotNull UseHide useHide) {
    return new SerUseHide(
      useHide.list().map(SerName::from),
      useHide.strategy() == UseHide.Strategy.Using
    );
  }

  public @NotNull UseHide toUseHide() {
    return new UseHide(names.map(SerName::toName), isUsing ? UseHide.Strategy.Using : UseHide.Strategy.Hiding);
  }
}
