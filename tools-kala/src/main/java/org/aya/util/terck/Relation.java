// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.terck;

import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

/**
 * Relations between size of formal function parameter and function argument
 * in one recursive call.
 * A semi-ring with zero = {@link #unk()}, one = {@link #eq()}.
 *
 * @author kiva
 */
@Debug.Renderer(text = "toDoc().debugRender()")
public sealed interface Relation extends Docile, Selector.Candidate<Relation> {
  /** increase or unrelated of callee argument wrt. caller parameter. */
  enum Unknown implements Relation {
    INSTANCE
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
      case Decrease d when d.size == 0 -> Doc.plain("  =");
      case Decrease d -> Doc.plain(STR."\{d.usable ? " " : "!"}-\{d.size}");
      case Unknown ignored -> Doc.plain("  ?");
    };
  }

  @Contract(pure = true) default @NotNull Relation mul(@NotNull Relation rhs) {
    if (rhs instanceof Unknown) return rhs;
    return switch (this) {
      case Unknown lhs -> lhs;
      case Decrease l when rhs instanceof Decrease r -> decr(l.usable || r.usable, l.size + r.size);
      default -> throw new AssertionError("unreachable");
    };
  }

  /** @return the side that decreases more */
  @Contract(pure = true) default @NotNull Relation add(@NotNull Relation rhs) {
    return switch (this.compare(rhs)) {
      case Lt -> rhs;   // rhs decreases more
      case Eq -> this;  // randomly pick one
      case Gt -> this;  // this decreases more
      case Unk -> throw new AssertionError("unreachable");
    };
  }
  /**
   * Compare two relations by their decrease amount.
   *
   * @return {@link Selector.DecrOrd#Lt} if this decreases less than the other,
   * {@link Selector.DecrOrd#Gt} if this decreases more.
   */
  @Override default @NotNull Selector.DecrOrd compare(@NotNull Relation other) {
    if (this.isUnknown() && other.isUnknown()) return Selector.DecrOrd.Eq;
    // Unknown means no decrease, so it's always less than any decrease
    if (this.isUnknown() && !other.isUnknown()) return Selector.DecrOrd.Lt;
    if (!this.isUnknown() && other.isUnknown()) return Selector.DecrOrd.Gt;
    var ldec = (Decrease) this;
    var rdec = (Decrease) other;
    // Usable decreases are always greater than unusable ones, or
    // the larger the size is, the more the argument decreases.
    return Selector.DecrOrd.compareBool(ldec.usable, rdec.usable)
      .add(Selector.DecrOrd.compareInt(ldec.size, rdec.size));
  }

  default boolean isUnknown() {
    return this == Unknown.INSTANCE;
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
