// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import org.aya.syntax.compile.JitDef;
import org.aya.syntax.ref.ModulePath;
import org.aya.syntax.ref.QName;
import org.aya.util.binop.Assoc;
import org.aya.util.binop.OpDecl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A well-typed generic definition,
 * it can be a core def (like {@link DataDef}) or a compiled def (like {@link org.aya.syntax.compile.JitData}).<br/>
 * We have four "def-chain"s, take {@link ConDef} as an example:
 * <pre>
 *  TyckDef             TyckAnyDef   <-----   AnyDef   ----->  JitTeleDef
 *     |                    |                   |                  |
 *     v                    v                   v                  v
 *   ConDef  <- - -  ConDef.Delegate <----- ConDefLike ----->    JitCon
 * </pre>
 * where the arrows indicate mean "is superclass of".<br/>
 * <ul>
 *   <li>The first chain is "core def" chain, which are well-typed definition</li>
 *   <li>The second chain is "local def" chain, which may refer to a not yet tycked definition, i.e. tyck a recursive function</li>
 *   <li>The third chain is "generic def" chain</li>
 *   <li>The fourth chain is "complied def" chain, which are well-typed, compiled definition</li>
 * </ul>
 * Note that {@link ConDef.Delegate} <b>contains</b> a {@link ConDef} rather than a super class of.
 */
public sealed interface AnyDef extends OpDecl permits JitDef, ConDefLike, DataDefLike, FnDefLike, TyckAnyDef {
  /**
   * Returns which file level module this def lives in.
   */
  @NotNull ModulePath fileModule();

  /**
   * Returns which module this def lives in, have the same prefix as {@link #fileModule()}
   */
  @NotNull ModulePath module();
  @NotNull String name();
  @Nullable Assoc assoc();
  @NotNull QName qualifiedName();
}
