// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.syntax.concrete.Expr;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Nested structure is something consists of a head and a body, for example:
 * <ul>
 *   <li>{@link Expr.Lambda} is a nested structure, it has a {@link Expr.Param} as a head and a {@link Expr} as a body</li>
 *   <li>{@link Expr.Let} is a nested structure, it has a {@link Expr.LetBind} as a head and a {@link Expr} as a body</li>
 * </ul>
 * <p>
 * A Nested class is supposed to also be a {@link Term}
 */
public interface Nested<Param, Term, This extends Nested<Param, Term, This>> {
  /**
   * The head of a nested structure.
   * It looks like a parameter of a lambda expression, so I call it "param".
   */
  @NotNull Param param();

  /**
   * The body of a nested structure
   */
  @NotNull WithPos<Term> body();

  /**
   * The nested body of a nested structure
   *
   * @return null if the body is not {@link This}
   * @implSpec {@code tryNested == null || tryNested == body}
   */
  @SuppressWarnings("unchecked")
  default @Nullable WithPos<This> tryNested() {
    var body = body();
    var clazz = (Class<This>) getClass();
    var nested = clazz.isInstance(body.data()) ? clazz.cast(body) : null;

    return nested == null ? null : body.replace(nested);
  }

  @SuppressWarnings("unchecked")
  static <Param, Term, This extends Nested<Param, Term, This>>
  @NotNull Tuple2<ImmutableSeq<Param>, WithPos<Term>>
  destructNested(@NotNull WithPos<This> nested) {
    var telescope = MutableList.<Param>create();
    WithPos<This> nestedBody = nested;
    WithPos<Term> body = nested.map(x -> (Term) x);

    while (nestedBody != null) {
      telescope.append(nestedBody.data().param());
      body = nestedBody.data().body();
      nestedBody = nestedBody.data().tryNested();
    }

    return Tuple.of(telescope.toImmutableSeq(), body);
  }
}
