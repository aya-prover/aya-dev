// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

public interface DefConsumer extends TermConsumer {
  default void accept(@NotNull GenericDef def) {
    switch (def) {
      case FnDef fn -> fn.descent(this::apply, this::apply, m -> {
        accept(m);
        return m;
      });
      case GenericDef d -> d.descent(this::apply, this::apply);
    }
  }

  default void accept(@NotNull Term.Matching matching) {
    matching.descent(this::apply, this::apply);
  }

  private Term apply(Term t) {
    accept(t);
    return t;
  }
  private Pat apply(Pat p) {
    accept(p);
    return p;
  }
}
