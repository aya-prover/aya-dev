// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * A convenient interface to consume a `Term`.
 * This is desirable when you only need to inspect a term and produce some side effects.
 * One can specify the `pre` and `post` method which represents a recursive step in pre- and post-order respectively.
 * The overall traversal is obtained by recursively traversing the term in pre-order followed by a post-order traversal.
 *
 * @author wsx
 */
public interface TermConsumer extends Consumer<Term> {
  default void pre(@NotNull Term term) {}

  default void post(@NotNull Term term) {}

  default void pre(@NotNull Pat pat) {}

  default void post(@NotNull Pat pat) {}

  default void accept(@NotNull Term term) {
    pre(term);
    term.descent(t -> {
      accept(t);
      return t;
    }, p -> {
      accept(p);
      return p;
    });
    post(term);
  }

  default void accept(@NotNull Pat pat) {
    pre(pat);
    pat.descent(p -> {
      accept(p);
      return p;
    }, t -> {
      accept(t);
      return t;
    });
    post(pat);
  }
}
