// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.term.*;

/**
 * An `Unfolder<R>` provides a function `R -> Term` given an incremental unfolding function `R -> Tm<R>`.
 * Sometimes directly implementing this interface can be tedious and repetitive,
 * and we have more specialized unfolding interfaces that might be helpful.
 *
 * @author wsx
 */
public interface Unfolder<R> {
  Tm<R> unfold(R r);

  default Term unfolded(R r) {
    return Tm.cast(unfold(r).map(this::unfolded));
  }
}
