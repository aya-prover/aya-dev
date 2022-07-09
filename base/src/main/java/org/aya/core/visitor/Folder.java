package org.aya.core.visitor;

import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.core.term.*;

/**
 * A `Folder<R>` provides a function `Term -> R` given an incremental folding function `Tm<R> -> R`.
 * Sometimes directly implementing this interface can be tedious and repetitive,
 * and we have more specialized folding interfaces that might be helpful.
 *
 * @author wsx
 */
interface Folder<R> {
  R fold(Tm<R> tm);

  default R folded(Term term) {
    return fold(Tm.cast(term).map(this::folded));
  }
}
