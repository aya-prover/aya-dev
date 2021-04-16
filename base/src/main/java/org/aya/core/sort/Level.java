// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.sort;

import org.aya.api.ref.Var;
import org.aya.concrete.LevelPrevar;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 * @see LevelPrevar
 * @see org.aya.concrete.Expr.UnivExpr
 */
@Debug.Renderer(text = "toDoc().debugRender()")
public sealed interface Level extends Docile {
  @NotNull Level succ();
  default @NotNull Level subst(@NotNull LevelSubst subst) {
    return this;
  }

  /**
   * Unlike {@link Reference}, this one is the implicit polymorphic level.
   * It is related to the underlying definition.
   */
  record Polymorphic(int lift) implements Level {
    @Override public @NotNull Level succ() {
      return new Polymorphic(lift + 1);
    }

    @Override public @NotNull Doc toDoc() {
      return levelDoc(lift, "lp");
    }
  }

  static @NotNull Doc levelDoc(int lift, String name) {
    return Doc.plain(name + (lift > 0 ? " + " + lift : ""));
  }

  final class Infinity implements Level {
    public static final @NotNull Infinity INSTANCE = new Infinity();

    private Infinity() {
    }

    @Override public @NotNull Level succ() {
      return this;
    }

    @Override public @NotNull Doc toDoc() {
      return Doc.plain("oo");
    }
  }

  record Constant(int value) implements Level {
    @Override public @NotNull Level succ() {
      return new Constant(value + 1);
    }

    @Override public @NotNull Doc toDoc() {
      return Doc.plain(String.valueOf(value));
    }
  }

  record Reference(@NotNull Level.LVar ref, int lift) implements Level {
    public Reference(@NotNull Level.LVar ref) {
      this(ref, 0);
    }

    @Override public @NotNull Level succ() {
      return new Reference(ref, lift + 1);
    }

    @Override public @NotNull Level subst(@NotNull LevelSubst subst) {
      return subst.get(ref).getOrDefault(this);
    }

    @Override public @NotNull Doc toDoc() {
      return levelDoc(lift, ref.name());
    }
  }

  /**
   * Not inspired from Arend.
   * <a href="https://github.com/JetBrains/Arend/blob/master/base/src/main/java/org/arend/core/sort/Sort.java"
   * >Sort.java</a>
   *
   * @author ice1000
   */
  record Sort(@NotNull Level uLevel, @NotNull Level hLevel) {
    public static final @NotNull Level.Sort OMEGA = new Sort(Infinity.INSTANCE, Infinity.INSTANCE);

    public @NotNull Level.Sort substSort(@NotNull LevelSubst subst) {
      return new Sort(uLevel.subst(subst), hLevel.subst(subst));
    }

    @Contract(" -> new") public @NotNull Level.Sort succ() {
      return new Sort(uLevel.succ(), hLevel.succ());
    }
  }

  /**
   * @param bound true if this is a bound level var, otherwise it needs to be solved.
   *              In well-typed terms it should always be true.
   * @author ice1000
   */
  record LVar(
    @NotNull String name,
    boolean bound
  ) implements Var {
    @Override public boolean equals(@Nullable Object o) {
      return this == o;
    }

    @Override public int hashCode() {
      return System.identityHashCode(this);
    }
  }
}
