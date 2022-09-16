// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.trace;

import kala.collection.mutable.MutableList;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.core.term.Term;
import org.aya.ref.DefVar;
import org.aya.tyck.Result;
import org.aya.util.TreeBuilder;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Deque;

/**
 * @author ice1000
 */
public sealed interface Trace extends TreeBuilder.Tree<Trace> {
  class Builder extends TreeBuilder<Trace> {
    @VisibleForTesting public @NotNull Deque<MutableList<Trace>> getTops() {
      return tops;
    }
  }

  record LabelT(
    @NotNull SourcePos pos,
    @NotNull String label,
    @NotNull MutableList<@NotNull Trace> children
  ) implements Trace {
    public LabelT(@NotNull SourcePos pos, @NotNull String label) {
      this(pos, label, MutableList.create());
    }
  }

  record DeclT(
    @NotNull DefVar<?, ?> var, @NotNull SourcePos pos,
    @NotNull MutableList<@NotNull Trace> children
  ) implements Trace {
    public DeclT(@NotNull DefVar<?, ?> var, @NotNull SourcePos pos) {
      this(var, pos, MutableList.create());
    }
  }

  record ExprT(@NotNull Expr expr, @Nullable Term term, @NotNull MutableList<@NotNull Trace> children) implements Trace {
    public ExprT(@NotNull Expr expr, @Nullable Term term) {
      this(expr, term, MutableList.create());
    }
  }

  record UnifyT(
    @NotNull Term lhs, @NotNull Term rhs,
    @NotNull SourcePos pos, @Nullable Term type,
    @NotNull MutableList<@NotNull Trace> children
  ) implements Trace {
    public UnifyT(@NotNull Term lhs, @NotNull Term rhs, @NotNull SourcePos pos) {
      this(lhs, rhs, pos, null);
    }

    public UnifyT(@NotNull Term lhs, @NotNull Term rhs, @NotNull SourcePos pos, @Nullable Term type) {
      this(lhs, rhs, pos, type, MutableList.create());
    }
  }

  record TyckT(
    @NotNull Term term, @NotNull Term type,
    @NotNull SourcePos pos,
    @NotNull MutableList<@NotNull Trace> children
  ) implements Trace {
    public TyckT(@NotNull Result result, @NotNull SourcePos pos) {
      this(result.wellTyped(), result.type(), pos, MutableList.create());
    }
  }

  record PatT(
    @NotNull Term type, @NotNull Pattern pat,
    @NotNull SourcePos pos,
    @NotNull MutableList<@NotNull Trace> children
  ) implements Trace {
    public PatT(@NotNull Term type, @NotNull Pattern pat, @NotNull SourcePos pos) {
      this(type, pat, pos, MutableList.create());
    }
  }
}
