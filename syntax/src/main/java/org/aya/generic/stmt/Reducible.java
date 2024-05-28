// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic.stmt;

import kala.collection.Seq;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.Callable;
import org.jetbrains.annotations.NotNull;

/**
 * A marker that indicates something can be reduced/has a corresponding {@link Callable}.
 * This is used for making serialization safer,
 * therefore {@link org.aya.syntax.core.def.FnDef} doesn't implement this marker but {@link org.aya.syntax.compile.JitFn} does.
 */
public interface Reducible {
  /**
   * @param fallback return this when unable to reduce, it is acceptable that fallback is null.
   * @return not null if reduce successfully, fallback if unable to reduce
   */
  Term invoke(Term fallback, @NotNull Seq<@NotNull Term> args);
}
