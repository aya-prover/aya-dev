// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;

public record TermSubst(TermView view, Subst subst) implements TermView {
  @Override
  public Term initial() {
    return view.initial();
  }

  @Override
  public TermView subst(Subst subst) {
    return new TermSubst(view, subst.add(subst));
  }

  @Override
  public Term post(Term term) {
    return  switch (view.post(term)) {
      case RefTerm ref -> subst.map().getOption(ref.var()).map(Term::rename).getOrDefault(ref);
      case RefTerm.Field field -> subst.map().getOption(field.ref()).map(Term::rename).getOrDefault(field);
      case Term misc -> misc;
    };
  }
}
