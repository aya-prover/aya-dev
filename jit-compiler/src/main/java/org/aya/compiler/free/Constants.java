// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.ImmutableTreeSeq;
import kala.collection.mutable.MutableSeq;
import org.aya.compiler.free.data.MethodRef;
import org.aya.generic.stmt.Reducible;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.TupTerm;
import org.aya.syntax.core.term.call.ConCallLike;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class Constants {
  private Constants() { }

  public static final @NotNull ClassDesc CD_Term = FreeUtil.fromClass(Term.class);
  public static final @NotNull ClassDesc CD_Seq = FreeUtil.fromClass(Seq.class);
  public static final @NotNull ClassDesc CD_ImmutableSeq = FreeUtil.fromClass(ImmutableSeq.class);
  public static final @NotNull ClassDesc CD_MutableSeq = FreeUtil.fromClass(MutableSeq.class);

  // Term -> Term
  public static final @NotNull MethodRef CLOSURE = new MethodRef.Default(
    FreeUtil.fromClass(UnaryOperator.class),
    "apply",
    CD_Term, ImmutableSeq.of(CD_Term),
    true
  );

  // () -> Term
  public static final @NotNull MethodRef THUNK = new MethodRef.Default(
    FreeUtil.fromClass(Supplier.class),
    "get",
    CD_Term, ImmutableSeq.empty(),
    true
  );

  // ImmutableSeq from(Object[])
  public static final @NotNull MethodRef IMMSEQ = new MethodRef.Default(
    CD_ImmutableSeq,
    "from",
    CD_ImmutableSeq, ImmutableSeq.of(ConstantDescs.CD_Object.arrayType()),
    true
  );

  /**
   * @see MutableSeq#fill(int, Object)
   */
  public static final @NotNull MethodRef MUTSEQ = new MethodRef.Default(
    CD_MutableSeq,
    "fill",
    CD_MutableSeq, ImmutableSeq.of(ConstantDescs.CD_int, ConstantDescs.CD_Object),
    true
  );

  /**
   * @see MutableSeq#set(int, Object)
   */
  public static final @NotNull MethodRef MUTSEQ_SET = new MethodRef.Default(
    CD_MutableSeq, "set", ConstantDescs.CD_void,
    ImmutableSeq.of(ConstantDescs.CD_int, ConstantDescs.CD_Object),
    true
  );

  /**
   * Remember to {@code checkcast} the result value!!
   *
   * @see Seq#get(int)
   */
  public static final @NotNull MethodRef SEQ_GET = new MethodRef.Default(
    CD_Seq, "get", ConstantDescs.CD_Object,
    ImmutableSeq.of(ConstantDescs.CD_int),
    true
  );

  public static final @NotNull MethodRef IMMTREESEQ = new MethodRef.Default(
    FreeUtil.fromClass(ImmutableTreeSeq.class),
    "from",
    FreeUtil.fromClass(ImmutableTreeSeq.class),
    ImmutableSeq.of(ConstantDescs.CD_Object.arrayType()),
    false
  );

  public static final @NotNull MethodRef BETAMAKE = new MethodRef.Default(
    FreeUtil.fromClass(BetaRedex.class),
    "make",
    CD_Term, ImmutableSeq.empty(),
    true
  );

  /**
   * @see Term#elevate(int)
   */
  public static final @NotNull MethodRef ELEVATE = new MethodRef.Default(
    CD_Term, "elevate", CD_Term, ImmutableSeq.of(ConstantDescs.CD_int), true
  );

  /**
   * @see Reducible#invoke(Supplier, Seq)
   */
  public static final @NotNull MethodRef REDUCIBLE_INVOKE = new MethodRef.Default(
    FreeUtil.fromClass(Reducible.class), "invoke",
    CD_Term, ImmutableSeq.of(FreeUtil.fromClass(Supplier.class), CD_Seq), true
  );

  /**
   * @see Closure#mkConst(Term)
   */
  public static final @NotNull MethodRef CLOSURE_MKCONST = new MethodRef.Default(
    FreeUtil.fromClass(Closure.class),
    "mkConst",
    FreeUtil.fromClass(Closure.class),
    ImmutableSeq.of(CD_Term),
    true
  );

  /**
   * @see Panic#unreachable()
   */
  public static final @NotNull MethodRef PANIC = new MethodRef.Default(
    FreeUtil.fromClass(Panic.class),
    "unreachable",
    ConstantDescs.CD_Object,
    ImmutableSeq.empty(),
    true
  );

  public static final @NotNull MethodRef INT_REPR = new MethodRef.Default(
    FreeUtil.fromClass(IntegerTerm.class),
    "repr",
    ConstantDescs.CD_int,
    ImmutableSeq.empty(),
    false
  );

  /**
   * @see ConCallLike#conArgs()
   */
  public static final @NotNull MethodRef CONARGS = new MethodRef.Default(
    FreeUtil.fromClass(ConCallLike.class),
    "conArgs",
    CD_ImmutableSeq,
    ImmutableSeq.empty(),
    true
  );

  /**
   * @see TupTerm#lhs()
   */
  public static final @NotNull MethodRef TUP_LHS = new MethodRef.Default(
    FreeUtil.fromClass(TupTerm.class),
    "lhs",
    CD_Term,
    ImmutableSeq.empty(),
    false
  );

  /**
   * @see TupTerm#rhs()
   */
  public static final @NotNull MethodRef TUP_RHS = new MethodRef.Default(
    FreeUtil.fromClass(TupTerm.class),
    "rhs",
    CD_Term,
    ImmutableSeq.empty(),
    false
  );
}
