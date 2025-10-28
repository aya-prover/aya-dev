// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.AyaDocile;
import org.aya.prettier.CorePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.xtt.EqTerm;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.telescope.AbstractTele;
import org.aya.util.ForLSP;
import org.aya.util.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A well-typed generic definition,
 * used before JIT compilation.
 *
 * @author zaoqi
 */
public sealed interface TyckDef extends AyaDocile permits MemberDef, SubLevelDef, TopLevelDef {
  @Override default @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new CorePrettier(options).def(this);
  }

  @ForLSP static @Nullable Term defType(@NotNull AnyDef var) {
    if (var instanceof TyckAnyDef<?> def && def.ref.signature == null) return null;
    var sig = var.signature();
    var names = sig.namesView().<Term>map(FreeTerm::new).toSeq();
    var result = sig.result(names);
    if (var instanceof ConDefLike con && con.hasEq()) result = new EqTerm(
      Closure.mkConst(result),
      con.equality(names, true),
      con.equality(names, false)
    );
    return result;
  }

  /**
   * @see AnyDef#signature()
   */
  static @NotNull AbstractTele.Locns defSignature(@NotNull DefVar<?, ?> defVar) {
    if (defVar.core != null) return defSignature(defVar.core);
    // guaranteed as this is already a core term
    var signature = defVar.signature;
    assert signature != null : defVar.name();
    return signature.telescope();
  }

  /// a raw signature of top-level def is always [Closed]
  static @Closed @NotNull AbstractTele.Locns defSignature(@NotNull TyckDef core) {
    return new AbstractTele.Locns(core.telescope(), core.result());
  }

  @NotNull DefVar<?, ?> ref();
  @NotNull Term result();
  @NotNull ImmutableSeq<Param> telescope();
}
