// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.generic.AyaDocile;
import org.aya.prettier.CorePrettier;
import org.aya.pretty.doc.Doc;
import org.aya.ref.DefVar;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * @author zaoqi
 */
public sealed interface GenericDef extends AyaDocile permits ClassDef, Def {
  @NotNull DefVar<?, ?> ref();

  void descentConsume(@NotNull Consumer<Term> f, @NotNull Consumer<Pat> g);

  @Override default @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new CorePrettier(options).def(this);
  }
}
