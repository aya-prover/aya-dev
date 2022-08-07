// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.term.Term;

import java.util.function.Consumer;

public interface EndoConsumer extends Consumer<Term> {
  default void pre(Term term) {}

  default void post(Term term) {}

  default void accept(Term term) {
    pre(term);
    term.descent(t -> {
      accept(t);
      return t;
    });
    post(term);
  }
}
