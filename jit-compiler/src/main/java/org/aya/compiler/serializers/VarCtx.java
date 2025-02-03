// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.Constants;
import org.aya.compiler.free.FreeCodeBuilder;
import org.aya.compiler.free.FreeJavaExpr;
import org.aya.compiler.free.data.LocalVariable;
import org.aya.compiler.free.morphism.asm.AsmVariable;
import org.jetbrains.annotations.NotNull;

/// Stores the context/representation needed for variable management. This class handles
/// the distinctions in variable representation between Java Source and Bytecode code
/// generation (see #1302).
public interface VarCtx {
  @NotNull FreeJavaExpr get(@NotNull FreeCodeBuilder cb, int index);
  void set(@NotNull FreeCodeBuilder cb, int index, @NotNull FreeJavaExpr value);
  
  /// Represents the variable context in a runtime {@link kala.collection.mutable.MutableSeq}
  /// of {@link Object}. This representation is used in Java Source and AST generation
  /// to avoid "captured variables must be final or effectively final" while performing
  /// assignments in pattern matching.
  ///
  /// @param seq The created variable for storing the runtime local variable sequence.
  record SeqView(LocalVariable seq) implements VarCtx {
    @Override
    public @NotNull FreeJavaExpr get(@NotNull FreeCodeBuilder cb, int index) {
      return cb.invoke(Constants.SEQ_GET, seq.ref(), ImmutableSeq.of(cb.iconst(index)));
    }
    @Override
    public void set(@NotNull FreeCodeBuilder cb, int index, @NotNull FreeJavaExpr value) {
      cb.exec(
        cb.invoke(Constants.MUTSEQ_SET, seq.ref(), ImmutableSeq.of(cb.iconst(index), value))
      );
    }
  }

  /// Represents the variable context as separate local variables as an optimization
  /// to {@link SeqView}. This can be used for Bytecode targets due to the lack of
  /// Java compile-time constraint on immutability of captured variables.
  record SepVars(ImmutableSeq<AsmVariable> seq) implements VarCtx {
    @Override
    public @NotNull FreeJavaExpr get(@NotNull FreeCodeBuilder cb, int index) {
      return seq.get(index).ref();
    }
    @Override
    public void set(@NotNull FreeCodeBuilder cb, int index, @NotNull FreeJavaExpr value) {
      cb.updateVar(seq.get(index), value);
    }
  }
}
