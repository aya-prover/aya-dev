// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import org.aya.syntax.compile.JitData;
import org.aya.syntax.compile.JitDef;
import org.aya.syntax.ref.*;
import org.aya.syntax.telescope.AbstractTele;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.OpDecl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// A well-typed generic definition,
/// it can be a core def (like [DataDef]) or a compiled def (like [JitData]).
/// We have four "def-chain"s, take [ConDef] as an example:
/// ```
///  TyckDef             TyckAnyDef   <-----   AnyDef   ----->  JitTeleDef
///     |                    |                   |                  |
///     v                    v                   v                  v
///   ConDef  <- - -  ConDef.Delegate <----- ConDefLike ----->    JitCon
/// ```
/// where the arrows mean "is superclass of".
///
/// - The first chain is "core def" chain, which are well-typed definition
/// - The second chain is "local def" chain, which may refer to a not yet tycked definition, i.e. tyck a recursive function
/// - The third chain is "generic def" chain
/// - The fourth chain is "complied def" chain, which are well-typed, compiled definition
///
/// Note that [ConDef.Delegate] **contains** a [ConDef] rather than a super class of.
public sealed interface AnyDef extends OpDecl
  permits JitDef, ClassDefLike, ConDefLike, DataDefLike, FnDefLike, MemberDefLike, PrimDefLike, TyckAnyDef {
  /**
   * Returns which file level module this def lives in.
   */
  @NotNull ModulePath fileModule();

  /// Returns which module this def lives in, have the same prefix as [#fileModule()]
  @NotNull ModulePath module();
  @NotNull String name();
  @Nullable Assoc assoc();
  @NotNull QName qualifiedName();

  static @NotNull AnyDef fromVar(@NotNull AnyDefVar defVar) {
    return switch (defVar) {
      case CompiledVar compiledVar -> compiledVar.core();
      case DefVar<?, ?> var when var.core != null -> TyckAnyDef.make(var.core);
      case DefVar<?, ?> var -> new TyckAnyDef<>(var);
    };
  }

  static @NotNull AnyDefVar toVar(@NotNull AnyDef defVar) {
    return switch (defVar) {
      case JitDef jitDef -> new CompiledVar(jitDef);
      case TyckAnyDef<?> tyckAnyDef -> tyckAnyDef.ref;
      default -> throw new UnsupportedOperationException("TODO");
    };
  }

  @NotNull AbstractTele signature();
}
