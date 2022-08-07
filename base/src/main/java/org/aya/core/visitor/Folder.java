// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.term.*;

import java.util.function.Function;

/**
 * A `Folder<R>` provides a function `Term -> R` given an incremental folding function `Tm<R> -> R`.
 * Sometimes directly implementing this interface can be tedious and repetitive,
 * and we have more specialized folding interfaces that might be helpful.
 *
 * @author wsx
 */
interface Folder<R> extends Function<Term, R> {
  R fold(Tm<R> tm);

  default R apply(Term term) {
    return fold(Tm.cast(term).map(this));
  }
}
