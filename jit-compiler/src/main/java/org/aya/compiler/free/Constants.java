// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.free;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.ImmutableTreeSeq;
import kala.collection.mutable.MutableSeq;
import kala.control.Result;
import org.aya.compiler.free.data.FieldRef;
import org.aya.compiler.free.data.MethodRef;
import org.aya.generic.stmt.Shaped;
import org.aya.syntax.compile.*;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.pat.PatMatcher;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.TupTerm;
import org.aya.syntax.core.term.call.ConCallLike;
import org.aya.syntax.core.term.call.RuleReducer;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class Constants {
  private Constants() { }

  public static final @NotNull ClassDesc CD_Term = FreeUtil.fromClass(Term.class);
  public static final @NotNull ClassDesc CD_Seq = FreeUtil.fromClass(Seq.class);
  public static final @NotNull ClassDesc CD_ImmutableSeq = FreeUtil.fromClass(ImmutableSeq.class);
  public static final @NotNull ClassDesc CD_MutableSeq = FreeUtil.fromClass(MutableSeq.class);
  public static final @NotNull ClassDesc CD_Thunk = FreeUtil.fromClass(Supplier.class);
  public static final @NotNull ClassDesc CD_Result = FreeUtil.fromClass(Result.class);

  // Term -> Term
  public static final @NotNull MethodRef CLOSURE = new MethodRef.Default(
    FreeUtil.fromClass(UnaryOperator.class),
    "apply",
    ConstantDescs.CD_Object, ImmutableSeq.of(ConstantDescs.CD_Object),
    true
  );

  // () -> Term
  public static final @NotNull MethodRef THUNK = new MethodRef.Default(
    FreeUtil.fromClass(Supplier.class),
    "get",
    ConstantDescs.CD_Object, ImmutableSeq.empty(),
    true
  );

  public static final @NotNull MethodRef FUNCTION = new MethodRef.Default(
    FreeUtil.fromClass(Function.class),
    "apply",
    ConstantDescs.CD_Object, ImmutableSeq.of(ConstantDescs.CD_Object),
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

  /**
   * @see Seq#toImmutableSeq()
   */
  public static final @NotNull MethodRef SEQ_TOIMMSEQ = new MethodRef.Default(
    CD_Seq, "toImmutableSeq", CD_ImmutableSeq, ImmutableSeq.empty(), true
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
   * @see RuleReducer#make()
   */
  public static final @NotNull MethodRef RULEREDUCER_MAKE = new MethodRef.Default(
    FreeUtil.fromClass(RuleReducer.class),
    "make",
    CD_Term, ImmutableSeq.empty(),
    true
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

  /**
   * @see Result#ok(Object)
   */
  public static final @NotNull MethodRef RESULT_OK = new MethodRef.Default(
    CD_Result, "ok",
    CD_Result, ImmutableSeq.of(ConstantDescs.CD_Object),
    true
  );

  public static final @NotNull MethodRef RESULT_ERR = new MethodRef.Default(
    CD_Result, "err",
    CD_Result, ImmutableSeq.of(ConstantDescs.CD_Object),
    true
  );

  /**
   * @see org.aya.syntax.telescope.JitTele#JitTele(int, boolean[], String[])
   */
  public static final @NotNull ImmutableSeq<ClassDesc> JIT_TELE_CON_PARAMS = ImmutableSeq.of(
    ConstantDescs.CD_int, ConstantDescs.CD_boolean.arrayType(), ConstantDescs.CD_String.arrayType()
  );

  public static final @NotNull FieldRef JITDATA_CONS = new FieldRef.Default(
    FreeUtil.fromClass(JitData.class),
    FreeUtil.fromClass(JitCon.class).arrayType(),
    "constructors"
  );

  public static final @NotNull FieldRef JITCLASS_MEMS = new FieldRef.Default(
    FreeUtil.fromClass(JitClass.class),
    FreeUtil.fromClass(JitMember.class).arrayType(),
    "members"
  );

  /**
   * @see UnaryOperator#identity()
   */
  public static final @NotNull MethodRef CLOSURE_ID = new MethodRef.Default(
    FreeUtil.fromClass(UnaryOperator.class),
    "identity",
    FreeUtil.fromClass(UnaryOperator.class),
    ImmutableSeq.empty(),
    true
  );

  /**
   * @see PatMatcher#apply(ImmutableSeq, ImmutableSeq)
   */
  public static final @NotNull MethodRef PATMATCHER_APPLY = new MethodRef.Default(
    FreeUtil.fromClass(PatMatcher.class), "apply",
    CD_Result, ImmutableSeq.of(CD_ImmutableSeq, CD_ImmutableSeq), false
  );

  /**
   * @see org.aya.syntax.compile.JitMatchy#invoke(Seq, Seq)
   */
  public static final @NotNull MethodRef MATCHY_INVOKE = new MethodRef.Default(
    FreeUtil.fromClass(JitMatchy.class),
    "invoke",
    CD_Term, ImmutableSeq.of(CD_Seq, CD_Seq),
    false
  );
}
