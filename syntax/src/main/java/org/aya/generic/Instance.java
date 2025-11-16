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

  // TODO: seems incorrect, [ref] should be able to be bind so that it refer to meta args
  record Local(@NotNull FreeTermLike ref, @Override @NotNull ClassCall type) implements Instance {
    @Override
    public @NotNull Instance bindTele(@NotNull SeqView<LocalVar> vars) {
      return new Local(ref, (ClassCall) type.bindTele(vars));
    }

    @Override
    public @NoInherit @NotNull Instance instTele(@NotNull SeqView<Term> tele) {
      return new Local(ref, (ClassCall) type.instTele(tele));
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
