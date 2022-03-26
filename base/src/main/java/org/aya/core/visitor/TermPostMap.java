// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.term.Term;

import java.util.function.Function;

public record TermPostMap(TermView view, Function<Term, Term> f) implements TermView {
  @Override
  public Term initial() {
    return view.initial();
  }

  @Override
  public TermView postMap(Function<Term, Term> g) {
    return new TermPostMap(view, g.compose(f));
  }

  @Override
  public Term post(Term term) {
    return f.apply(view.post(term));
  }
}
