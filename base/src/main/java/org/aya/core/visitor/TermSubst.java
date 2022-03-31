// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;

public record TermSubst(TermView view, Substituter.TermSubst subst) implements TermView {
  @Override
  public Term initial() {
    return view.initial();
  }

  @Override
  public TermView subst(Substituter.TermSubst subst) {
    return new TermSubst(view, subst.add(subst));
  }

  @Override
  public Term post(Term term) {
    var processed = view.post(term);
    return subst.isEmpty() ? processed : switch (processed) {
      case RefTerm ref -> subst.map().getOrDefault(ref.var(), ref);
      case RefTerm.Field field -> subst.map().getOrDefault(field.ref(), field);
      default -> processed;
    };
  }
}
