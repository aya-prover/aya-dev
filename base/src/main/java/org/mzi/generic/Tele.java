// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.generic;

import asia.kala.PrimitiveTuples;
import asia.kala.collection.Seq;
import asia.kala.function.IndexedConsumer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.mzi.api.ref.Bind;
import org.mzi.api.ref.Var;

/**
 * Similar to Arend <code>DependentLink</code>.
 * If we have <code>{A : U} (a b : A)</code>, then it should be translated into:
 * <pre>
 * {@link Tele.TypedTele}(A, {@link org.mzi.core.term.UnivTerm}, false,
 *   {@link Tele.NamedTele}(a, {@link Tele.TypedTele}(b, A, true, null)))
 * </pre>
 *
 * @author ice1000
 */
public interface Tele<Term> extends Bind<Term> {
  @Override @Nullable Tele<Term> next();
  @Override @NotNull Term type();

  <P, R> R accept(@NotNull Tele.Visitor<Term, P, R> visitor, P p);

  default PrimitiveTuples.@NotNull IntObjTuple2<@NotNull Tele<Term>>
  forEach(@NotNull IndexedConsumer<@NotNull Tele<Term>> consumer) {
    var tele = this;
    var i = 0;
    while (true) {
      consumer.accept(i++, tele);
      if (tele.next() == null) break;
      else tele = tele.next();
    }
    return new PrimitiveTuples.IntObjTuple2<>(i, tele);
  }

  default @NotNull Tele<Term> last() {
    return forEach((index, tele) -> {})._2;
  }

  default int size() {
    return forEach((index, tele) -> {})._1;
  }

  @TestOnly @Contract(pure = true)
  default boolean checkSubst(@NotNull Seq<@NotNull Arg<Term>> args) {
    var obj = new Object() {
      boolean ok = true;
    };
    forEach((i, tele) -> obj.ok = obj.ok && tele.explicit() == args.get(i).explicit());
    return obj.ok;
  }

  interface Visitor<Term, P, R> {
    R visitTyped(@NotNull TypedTele<Term> typed, P p);
    R visitNamed(@NotNull NamedTele<Term> named, P p);
  }

  record TypedTele<Term>(
    @NotNull Var ref,
    @NotNull Term type,
    boolean explicit,
    @Nullable Tele<Term> next
  ) implements Tele<Term> {
    @Override public <P, R> R accept(@NotNull Visitor<Term, P, R> visitor, P p) {
      return visitor.visitTyped(this, p);
    }
  }

  /**
   * @author ice1000
   */
  record NamedTele<Term>(
    @NotNull Var ref,
    @NotNull Tele<Term> next
  ) implements Tele<Term> {
    @Contract(pure = true) @Override public boolean explicit() {
      return next().explicit();
    }

    @Contract(pure = true) @Override public @NotNull Term type() {
      return next().type();
    }

    @Override public <P, R> R accept(@NotNull Visitor<Term, P, R> visitor, P p) {
      return visitor.visitNamed(this, p);
    }
  }
}
