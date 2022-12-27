// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core;

import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public interface UntypedParam {
  @Contract(pure = true) boolean explicit();
  @Contract(pure = true) @NotNull LocalVar ref();

  @Contract(" -> new") default @NotNull Arg<@NotNull Term> toArg() {
    return new Arg<>(toTerm(), explicit());
  }

  @Contract(" -> new") default @NotNull RefTerm toTerm() {
    return new RefTerm(ref());
  }


  @Contract(" -> new") default @NotNull LocalVar renameVar() {
    return ref().rename();
  }
}
