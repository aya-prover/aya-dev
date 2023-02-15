// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

public interface DefVisitor extends EndoTerm {
  default void accept(@NotNull GenericDef def) {
    switch (def) {
      case FnDef fn -> {
        fn.telescope.forEach(p -> p.descentConsume(this::accept));
        this.accept(fn.result);
        fn.body.forEach(this::accept, matchings -> matchings.forEach(this::accept));
      }
      case GenericDef d -> d.descentConsume(this::accept, this::accept);
    }
  }

  // TODO: HACK! Special method to support hooking into the transformation of `Matching`
  //   This allows `CallResolver` to check termination based on the top level pattern clauses.
  //   But we now also support pattern matching within terms, so is this rather fragile?
  default void accept(@NotNull Term.Matching matching) {
    matching.descentConsume(this::accept, this::accept);
  }

  private void accept(Term t) {
    apply(t);
  }
  private void accept(Pat p) {
    apply(p);
  }
}
