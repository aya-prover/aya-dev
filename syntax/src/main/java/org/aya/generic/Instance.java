// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import kala.collection.SeqView;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.annotation.Bound;
import org.aya.syntax.core.annotation.NoInherit;
import org.aya.syntax.core.def.FnDefLike;
import org.aya.syntax.core.term.FreeTermLike;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.ClassCall;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.PrettierOptions;
import org.jetbrains.annotations.NotNull;

public sealed interface Instance extends AyaDocile {
  @Bound @NotNull Instance bindTele(@NotNull SeqView<LocalVar> vars);
  @NoInherit @NotNull Instance instTele(@NotNull SeqView<Term> tele);

  /// @param ref is either [org.aya.syntax.core.term.FreeTermLike] or [org.aya.syntax.core.term.LocalTerm]
  record Local(@NotNull Term ref, @Override @NotNull ClassCall type) implements Instance {
    public static @NotNull Local of(@NotNull FreeTermLike term, @NotNull ClassCall type) {
      return new Local(term, type);
    }

    @Override
    public @NotNull Instance bindTele(@NotNull SeqView<LocalVar> vars) {
      return new Local(ref.bindTele(vars), (ClassCall) type.bindTele(vars));
    }

    @Override
    public @NoInherit @NotNull Instance instTele(@NotNull SeqView<Term> tele) {
      return new Local(ref.instTele(tele), (ClassCall) type.instTele(tele));
    }
  }

  /// @param def may have parameters, but must be used in result
  record Global(@NotNull FnDefLike def) implements Instance {
    @Override
    public @NotNull Instance bindTele(@NotNull SeqView<LocalVar> vars) {
      return this;
    }

    @Override
    public @NoInherit @NotNull Instance instTele(@NotNull SeqView<Term> tele) {
      return this;
    }
  }

  @Override
  default @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return switch (this) {
      case Global(var def) -> throw new UnsupportedOperationException("TODO");
      case Local(var ref, _) -> ref.toDoc(options);
    };
  }
}
