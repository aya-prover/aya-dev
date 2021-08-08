// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.hatate;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.value.Ref;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.Var;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.Expr;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.visitor.ExprResolver;
import org.aya.concrete.visitor.ExprFixpoint;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public sealed interface Literate {
  default void resolve(@NotNull BinOpSet opSet, @NotNull Context context) {
  }

  record Raw(@NotNull Doc content) implements Literate {
  }

  record Styled(@Nullable Style style, @NotNull ImmutableSeq<Literate> content) implements Literate {
  }

  record Err(@NotNull Ref<Var> def, @NotNull SourcePos sourcePos) implements Literate {
    @Override public void resolve(@NotNull BinOpSet opSet, @NotNull Context context) {
      def.set(context.getUnqualified(def.value.name(), sourcePos));
    }
  }

  /**
   * @param isType <code>true</code> if show type, otherwise show the term itself
   * @param mode   <code>null</code> if as-is
   */
  record CodeCmd(boolean isType, @Nullable NormalizeMode mode) {
  }

  record Code(@NotNull Ref<Expr> expr, @NotNull CodeCmd cmd) implements Literate {
    @Contract(mutates = "this")
    public <P> void modify(@NotNull ExprFixpoint<P> fixpoint, P p) {
      expr.set(expr.value.accept(fixpoint, p));
    }

    @Override public void resolve(@NotNull BinOpSet opSet, @NotNull Context context) {
      modify(new ExprResolver(false, Buffer.create()), context);
    }
  }

  record Par(@NotNull ImmutableSeq<Literate> children) implements Literate {
    @Override public void resolve(@NotNull BinOpSet opSet, @NotNull Context context) {
      children.forEach(child -> child.resolve(opSet, context));
    }
  }
}
