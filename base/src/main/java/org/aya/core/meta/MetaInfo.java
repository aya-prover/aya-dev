// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.meta;

import org.aya.core.term.SortTerm;
import org.aya.core.term.Term;
import org.aya.generic.AyaDocile;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.unify.Synthesizer;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Constraints on a meta variable.
 *
 * @author ice1000
 */
public sealed interface MetaInfo extends AyaDocile {
  @Nullable Term result();
  boolean isType(@NotNull Synthesizer synthesizer);
  /**
   * The type of the meta is known.
   * We shall check the solution against this type.
   */
  record Result(@NotNull Term result) implements MetaInfo {
    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.symbol("?"), Doc.symbol(":"), result.toDoc(options));
    }

    @Override public boolean isType(@NotNull Synthesizer synthesizer) {
      return synthesizer.tryPress(result) instanceof SortTerm;
    }
  }

  /**
   * The meta variable is a type.
   * It should be able to appear on the RHS of a judgment.
   */
  enum AnyType implements MetaInfo {
    INSTANCE;

    @Override public @Nullable Term result() {
      return null;
    }

    @Override public boolean isType(@NotNull Synthesizer synthesizer) {
      return true;
    }

    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.plain("_"), Doc.symbols(":", "?"));
    }
  }

  /**
   * The meta variable is the domain of a pi type which is of a known type.
   * <p>
   * See: <code>notes/sort-system.md</code>
   */
  record PiDom(@NotNull SortTerm sort) implements MetaInfo {
    @Override public @Nullable Term result() {
      return null;
    }

    @Override public boolean isType(@NotNull Synthesizer synthesizer) {
      return true;
    }

    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.symbols("?", "->", "_", ":"), sort.toDoc(options));
    }
  }
}
