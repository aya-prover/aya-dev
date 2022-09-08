// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import org.aya.generic.util.InternalException;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

/**
 * Relations between size of formal function parameter and function argument
 * in one recursive call. Together with the two operations {@link Relation#add(Relation)}
 * and {@link Relation#mul(Relation)} the relation set forms a commutative semi-ring
 * with zero {@link Relation#unk()} and unit {@link Relation#eq()}.
 *
 * @author kiva
 */
@Debug.Renderer(text = "toDoc().debugRender()")
public sealed interface Relation extends Docile {
  /** increase or unrelated of callee argument wrt. caller parameter. */
  record Unknown() implements Relation {
    public static final @NotNull Unknown INSTANCE = new Unknown();
  }

  /**
   * decrease of callee argument wrt. caller parameter
   *
   * @param size   The larger the size, the smaller the callee argument.
   * @param usable Currently unused, for coinductive. Are we going to have it?
   */
  record Decrease(boolean usable, int size) implements Relation {
  }

  @Override default @NotNull Doc toDoc() {
    return switch (this) {
      case Decrease d && d.size == 0 -> Doc.plain("  =");
      case Decrease d -> Doc.plain((d.usable ? " " : "!") + "-" + d.size);
      case Unknown ignored -> Doc.plain("  ?");
    };
  }

  @Contract(pure = true) default @NotNull Relation mul(@NotNull Relation rhs) {
    if (rhs instanceof Unknown) return rhs;
    return switch (this) {
      case Unknown lhs -> lhs;
      case Decrease l && rhs instanceof Decrease r -> decr(l.usable || r.usable, l.size + r.size);
      default -> throw new InternalException("unreachable");
    };
  }

  @Contract(pure = true) default @NotNull Relation add(@NotNull Relation rhs) {
    return this.lessThanOrEqual(rhs) ? rhs : this;
  }

  /**
   * A relation <code>A</code> is less than another relation <code>B</code> iff:
   * <code>A</code> contains more uncertainty than <code>B</code>.
   */
  default boolean lessThanOrEqual(@NotNull Relation rhs) {
    return switch (this) {
      case Decrease l && rhs instanceof Decrease r && !l.usable && r.usable && r.size > 0 -> true;
      case Decrease l && rhs instanceof Decrease r && l.usable && !r.usable && l.size > 0 -> false;
      case Decrease l && rhs instanceof Decrease r -> r.size >= l.size;
      case Unknown l -> true;
      case Relation l && rhs instanceof Unknown -> false;
      default -> throw new InternalException("unreachable");
    };
  }

  default boolean isDecreasing() {
    return this instanceof Decrease d && d.usable && d.size > 0;
  }

  static @NotNull Relation fromCompare(int compare) {
    if (compare == 0) return decr(true, 0);
    if (compare < 0) return decr(true, -compare);
    return unk();
  }

  static @NotNull Relation decr(boolean usable, int size) {
    return new Decrease(usable, size);
  }

  static @NotNull Relation eq() {
    return decr(true, 0);
  }

  static @NotNull Relation lt() {
    return decr(true, 1);
  }

  static @NotNull Relation unk() {
    return Unknown.INSTANCE;
  }
}
