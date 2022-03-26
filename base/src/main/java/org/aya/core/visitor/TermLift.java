// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.term.Term;

public record TermLift(TermView view, int ulift) implements TermView {
  @Override
  public Term initial() {
    return view.initial();
  }

  @Override
  public TermView lift(int shift) {
    return new TermLift(view, ulift + shift);
  }

  @Override
  public Term post(Term term) {
    // TODO: Implement universe lifting.
    return view.post(term);
  }
}
