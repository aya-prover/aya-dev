// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.morphism;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.ImmutableTreeSeq;
import kala.collection.mutable.MutableSeq;
import kala.control.Result;
import org.aya.compiler.FieldRef;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.ir.IrCodeBuilder;
import org.aya.compiler.morphism.ir.IrVariable;
import org.aya.syntax.compile.JitClass;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.compile.JitMember;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.pat.PatMatcher;
import org.aya.syntax.core.term.LamTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.TupTerm;
import org.aya.syntax.core.term.call.ConCallLike;
import org.aya.syntax.core.term.call.RuleReducer;
import org.aya.syntax.core.term.marker.BetaRedex;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

public final class Constants {
  private Constants() { }

  public static final @NotNull ClassDesc CD_Term = JavaUtil.fromClass(Term.class);
  public static final @NotNull ClassDesc CD_Seq = JavaUtil.fromClass(Seq.class);
  public static final @NotNull ClassDesc CD_ImmutableSeq = JavaUtil.fromClass(ImmutableSeq.class);
  public static final @NotNull ClassDesc CD_MutableSeq = JavaUtil.fromClass(MutableSeq.class);
  public static final @NotNull ClassDesc CD_Thunk = JavaUtil.fromClass(Supplier.class);
  public static final @NotNull ClassDesc CD_Closure = JavaUtil.fromClass(Closure.class);
  public static final @NotNull ClassDesc CD_JitCon = JavaUtil.fromClass(JitCon.class);
  public static final @NotNull ClassDesc CD_IntegerTerm = JavaUtil.fromClass(IntegerTerm.class);
  public static final @NotNull ClassDesc CD_ConCallLike = JavaUtil.fromClass(ConCallLike.class);
  public static final @NotNull ClassDesc CD_UnaryOperator = JavaUtil.fromClass(UnaryOperator.class);
  public static final @NotNull ClassDesc CD_Result = JavaUtil.fromClass(Result.class);
  public static final @NotNull String NAME_OF = "of";
  public static final @NotNull String NAME_EMPTY = "empty";

  // Term -> Term
  public static final @NotNull MethodRef CLOSURE = new MethodRef(
    CD_UnaryOperator,
    "apply",
    ConstantDescs.CD_Object, ImmutableSeq.of(ConstantDescs.CD_Object),
    true
  );

  // () -> Term
  public static final @NotNull MethodRef THUNK = new MethodRef(
    JavaUtil.fromClass(Supplier.class),
    "get",
    ConstantDescs.CD_Object, ImmutableSeq.empty(),
    true
  );

  public static final @NotNull MethodRef FUNCTION = new MethodRef(
    JavaUtil.fromClass(Function.class),
    "apply",
    ConstantDescs.CD_Object, ImmutableSeq.of(ConstantDescs.CD_Object),
    true
  );

  /// @see ImmutableSeq#of(Object[])
  public static final @NotNull MethodRef IMMSEQ = new MethodRef(
    CD_ImmutableSeq,
    NAME_OF,
    CD_ImmutableSeq, ImmutableSeq.of(ConstantDescs.CD_Object.arrayType()),
    true
  );

  /// Remember to `checkcast` the result value!!
  ///
  /// @see Seq#get(int)
  public static final @NotNull MethodRef SEQ_GET = new MethodRef(
    CD_Seq, "get", ConstantDescs.CD_Object,
    ImmutableSeq.of(ConstantDescs.CD_int),
    true
  );

  /// @see Seq#toSeq()
  public static final @NotNull MethodRef SEQ_TOSEQ = new MethodRef(
    CD_Seq, "toSeq", CD_ImmutableSeq, ImmutableSeq.empty(), true
  );

  public static final @NotNull MethodRef IMMTREESEQ = new MethodRef(
    JavaUtil.fromClass(ImmutableTreeSeq.class),
    NAME_OF,
    JavaUtil.fromClass(ImmutableTreeSeq.class),
    ImmutableSeq.of(ConstantDescs.CD_Object.arrayType()),
    false
  );

  public static final @NotNull MethodRef BETAMAKE = new MethodRef(
    JavaUtil.fromClass(BetaRedex.class),
    "make",
    CD_Term, ImmutableSeq.empty(),
    true
  );

  /**
   * @see Term#elevate(int)
   */
  public static final @NotNull MethodRef ELEVATE = new MethodRef(
    CD_Term, "elevate", CD_Term, ImmutableSeq.of(ConstantDescs.CD_int), true
  );

  /**
   * @see RuleReducer#make()
   */
  public static final @NotNull MethodRef RULEREDUCER_MAKE = new MethodRef(
    JavaUtil.fromClass(RuleReducer.class),
    "make",
    CD_Term, ImmutableSeq.empty(),
    true
  );

  /**
   * @see Closure#mkConst(Term)
   */
  public static final @NotNull MethodRef CLOSURE_MKCONST = new MethodRef(
    CD_Closure,
    "mkConst",
    CD_Closure,
    ImmutableSeq.of(CD_Term),
    true
  );

  /**
   * @see Panic#unreachable()
   */
  public static final @NotNull MethodRef PANIC = new MethodRef(
    JavaUtil.fromClass(Panic.class),
    "unreachable",
    ConstantDescs.CD_Object,
    ImmutableSeq.empty(),
    false
  );

  public static final @NotNull MethodRef INT_REPR = new MethodRef(
    JavaUtil.fromClass(IntegerTerm.class),
    "repr",
    ConstantDescs.CD_int,
    ImmutableSeq.empty(),
    false
  );

  /**
   * @see ConCallLike#conArgs()
   */
  public static final @NotNull MethodRef CONARGS = new MethodRef(
    CD_ConCallLike,
    "conArgs",
    CD_ImmutableSeq,
    ImmutableSeq.empty(),
    true
  );

  /**
   * @see TupTerm#lhs()
   */
  public static final @NotNull MethodRef TUP_LHS = new MethodRef(
    JavaUtil.fromClass(TupTerm.class),
    "lhs",
    CD_Term,
    ImmutableSeq.empty(),
    false
  );

  /**
   * @see TupTerm#rhs()
   */
  public static final @NotNull MethodRef TUP_RHS = new MethodRef(
    JavaUtil.fromClass(TupTerm.class),
    "rhs",
    CD_Term,
    ImmutableSeq.empty(),
    false
  );

  /**
   * @see Result#ok(Object)
   */
  public static final @NotNull MethodRef RESULT_OK = new MethodRef(
    CD_Result, "ok",
    CD_Result, ImmutableSeq.of(ConstantDescs.CD_Object),
    true
  );

  public static final @NotNull MethodRef RESULT_ERR = new MethodRef(
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

  public static final @NotNull FieldRef JITDATA_CONS = new FieldRef(
    JavaUtil.fromClass(JitData.class),
    JavaUtil.fromClass(JitCon.class).arrayType(),
    "constructors"
  );

  public static final @NotNull FieldRef JITCLASS_MEMS = new FieldRef(
    JavaUtil.fromClass(JitClass.class),
    JavaUtil.fromClass(JitMember.class).arrayType(),
    "members"
  );

  /**
   * @see UnaryOperator#identity()
   */
  public static final @NotNull MethodRef CLOSURE_ID = new MethodRef(
    JavaUtil.fromClass(UnaryOperator.class),
    "identity",
    JavaUtil.fromClass(UnaryOperator.class),
    ImmutableSeq.empty(),
    true
  );

  public static @NotNull IrVariable unaryOperatorIdentity(@NotNull IrCodeBuilder builder) {
    return builder.invoke(CLOSURE_ID, ImmutableSeq.empty());
  }

  /**
   * @see PatMatcher#apply(ImmutableSeq, ImmutableSeq)
   */
  public static final @NotNull MethodRef PATMATCHER_APPLY = new MethodRef(
    JavaUtil.fromClass(PatMatcher.class), "apply",
    CD_Result, ImmutableSeq.of(CD_ImmutableSeq, CD_ImmutableSeq), false
  );

  public static final @NotNull MethodRef LAMBDA_NEW = new MethodRef(
    JavaUtil.fromClass(LamTerm.class),
    INIT_NAME,
    CD_void, ImmutableSeq.of(CD_Closure),
    false);
}
