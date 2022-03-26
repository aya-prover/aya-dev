// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.term.Term;

import java.util.function.Function;

public record TermPreMap(TermView view, Function<Term, Term> f) implements TermView {
  @Override
  public Term initial() {
    return view.initial();
  }

  @Override
  public TermView preMap(Function<Term, Term> g) {
    return new TermPostMap(view, f.compose(g));
  }

  @Override
  public Term pre(Term term) {
    return view.pre(f.apply(term));
  }
}
