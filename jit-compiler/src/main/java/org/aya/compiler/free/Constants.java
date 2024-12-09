// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.compiler.free.data.FieldData;
import org.aya.compiler.free.data.MethodData;
import org.aya.generic.stmt.Reducible;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class Constants {
  private Constants() { }

  public static final @NotNull ClassDesc CD_Term = FreeUtils.fromClass(Term.class);
  public static final @NotNull ClassDesc CD_Seq = FreeUtils.fromClass(Seq.class);
  public static final @NotNull ClassDesc CD_ImmutableSeq = FreeUtils.fromClass(ImmutableSeq.class);

  // Term -> Term
  public static final @NotNull MethodData CLOSURE = new MethodData.Default(
    FreeUtils.fromClass(UnaryOperator.class),
    "apply",
    CD_Term, ImmutableSeq.of(CD_Term),
    true
  );

  // () -> Term
  public static final @NotNull MethodData THUNK = new MethodData.Default(
    FreeUtils.fromClass(Supplier.class),
    "get",
    CD_Term, ImmutableSeq.empty(),
    true
  );

  // ImmutableSeq from(Object[])
  public static final @NotNull MethodData IMMSEQ = new MethodData.Default(
    CD_ImmutableSeq,
    "from",
    CD_ImmutableSeq, ImmutableSeq.of(ConstantDescs.CD_Object.arrayType()),
    false
  );

  public static final @NotNull MethodData BETAMAKE = new MethodData.Default(
    FreeUtils.fromClass(BetaRedex.class),
    "make",
    CD_Term, ImmutableSeq.empty(),
    true
  );

  /**
   * @see Term#elevate(int)
   */
  public static final @NotNull MethodData ELEVATE = new MethodData.Default(
    CD_Term, "elevate", CD_Term, ImmutableSeq.of(ConstantDescs.CD_int), true
  );

  /**
   * @see Reducible#invoke(Supplier, Seq)
   */
  public static final @NotNull MethodData REDUCIBLE_INVOKE = new MethodData.Default(
    FreeUtils.fromClass(Reducible.class), "invoke",
    CD_Term, ImmutableSeq.of(FreeUtils.fromClass(Supplier.class), CD_Seq), true
  );

  /**
   * @see Closure#mkConst(Term)
   */
  public static final @NotNull MethodData CLOSURE_MKCONST = new MethodData.Default(
    FreeUtils.fromClass(Closure.class),
    "mkConst",
    FreeUtils.fromClass(Closure.class),
    ImmutableSeq.of(CD_Term),
    true
  );
}
