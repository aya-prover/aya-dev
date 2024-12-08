// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free;

import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.data.MethodData;
import org.aya.syntax.core.term.Term;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class Constants {
  private Constants() { }

  public static final @NotNull ClassDesc CD_Term = FreeUtils.fromClass(Term.class);

  // Term -> Term
  public static final @NotNull MethodData CLOSURE = new MethodData.Default(
    FreeUtils.fromClass(UnaryOperator.class),
    "apply",
    CD_Term, ImmutableSeq.of(CD_Term)
  );

  // () -> Term
  public static final @NotNull MethodData THUNK = new MethodData.Default(
    FreeUtils.fromClass(Supplier.class),
    "get",
    CD_Term, ImmutableSeq.empty()
  );
}
