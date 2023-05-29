// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.aya.concrete.Expr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Nested structure is something consists of a head and a body, for example:
 * <ul>
 *   <li>{@link Expr.Lambda} is a nested structure, it has a {@link Expr.Param} as a head and a {@link Expr} as a body</li>
 *   <li>{@link Expr.Let} is a nested structure, it has a {@link Expr.LetBind} as a head and a {@link Expr} as a body</li>
 *   <li>If you wish, {@link Expr.App} is also a nested structure in some way</li>
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
  @NotNull Term body();

  /**
   * The nested body of a nested structure
   *
   * @return null if the body is not {@link This}
   * @implSpec {@code tryNested == null || tryNested == body}
   */
  @SuppressWarnings("unchecked")
  default @Nullable This tryNested() {
    var body = body();
    var clazz = getClass();

    return clazz.isInstance(body) ? (This) clazz.cast(body) : null;
  }
}
