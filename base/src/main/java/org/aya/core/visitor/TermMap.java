// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.term.Term;

import java.util.function.Function;

public record TermMap(TermView view, Function<Term, Term> pre, Function<Term, Term> post) implements TermView {
  @Override
  public Term initial() {
    return view.initial();
  }

  @Override
  public TermView preMap(Function<Term, Term> f) {
    return new TermMap(view, pre.compose(f), post);
  }

  @Override
  public TermView postMap(Function<Term, Term> f) {
    return new TermMap(view, pre, f.compose(post));
  }

  @Override
  public Term pre(Term term) {
    return view.pre(pre.apply(term));
  }

  @Override
  public Term post(Term term) {
    return post.apply(view.post(term));
  }
}
