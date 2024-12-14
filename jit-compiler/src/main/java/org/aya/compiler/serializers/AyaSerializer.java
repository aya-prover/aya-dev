// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.ImmutableTreeSeq;
import kala.collection.mutable.MutableSeq;
import kala.control.Result;
import org.aya.compiler.free.Constants;
import org.aya.compiler.free.FreeCodeBuilder;
import org.aya.compiler.free.FreeExprBuilder;
import org.aya.compiler.free.FreeJavaExpr;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.TupTerm;
import org.aya.syntax.core.term.call.*;
import org.aya.util.error.Panic;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

import static org.aya.compiler.serializers.ExprializeUtil.getJavaRef;

public interface AyaSerializer {
  String PACKAGE_BASE = "AYA";
  String STATIC_FIELD_INSTANCE = "INSTANCE";
  String FIELD_INSTANCE = "ref";
  String FIELD_EMPTYCALL = "ourCall";
  String METHOD_INVOKE = "invoke";
  String CLASS_CONCALL = getJavaRef(ConCall.class);
  String CLASS_CONCALLLIKE = getJavaRef(ConCallLike.class);
  String CLASS_TUPLE = getJavaRef(TupTerm.class);
  String CLASS_FNCALL = getJavaRef(FnCall.class);
  String CLASS_DATACALL = getJavaRef(DataCall.class);
  String CLASS_PRIMCALL = getJavaRef(PrimCall.class);
  String CLASS_IMMSEQ = getJavaRef(ImmutableSeq.class);
  String CLASS_PIMMSEQ = getJavaRef(ImmutableTreeSeq.class);
  String CLASS_MUTSEQ = getJavaRef(MutableSeq.class);
  String CLASS_SEQ = getJavaRef(Seq.class);
  String CLASS_TERM = getJavaRef(Term.class);
  String CLASS_PAT = getJavaRef(Pat.class);
  String CLASS_PANIC = getJavaRef(Panic.class);

  String CLASS_SUPPLIER = getJavaRef(Supplier.class);
  String CLASS_RESULT = getJavaRef(Result.class);
  String TYPE_IMMTERMSEQ = CLASS_IMMSEQ + "<" + CLASS_TERM + ">";

  @Language("Java") String IMPORT_BLOCK = """
    import org.aya.generic.term.SortKind;
    import org.aya.generic.term.DTKind;
    import org.aya.generic.State;
    import org.aya.generic.Modifier;
    import org.aya.syntax.compile.*;
    import org.aya.syntax.compile.CompiledAya;
    import org.aya.syntax.ref.LocalVar;
    import org.aya.syntax.core.*;
    import org.aya.syntax.core.Closure.Jit;
    import org.aya.syntax.core.pat.Pat;
    import org.aya.syntax.core.pat.PatMatcher;
    import org.aya.syntax.core.repr.*;
    import org.aya.syntax.core.term.*;
    import org.aya.syntax.core.term.repr.*;
    import org.aya.syntax.core.term.call.*;
    import org.aya.syntax.core.term.xtt.*;
    import org.aya.util.error.Panic;
    import org.aya.util.binop.Assoc;

    import java.util.function.Supplier;
    import kala.collection.immutable.ImmutableSeq;
    import kala.collection.immutable.ImmutableTreeSeq;
    import kala.collection.mutable.MutableSeq;
    import kala.collection.Seq;
    import kala.control.Result;
    """;

  static void returnPanic(@NotNull FreeCodeBuilder builder) {
    builder.returnWith(buildPanic(builder));
  }

  static void execPanic(@NotNull FreeCodeBuilder builder) {
    builder.exec(buildPanic(builder));
  }

  static @NotNull FreeJavaExpr buildPanic(@NotNull FreeExprBuilder builder) {
    return builder.invoke(Constants.PANIC, ImmutableSeq.empty());
  }

  /**
   * Build a type safe {@link Supplier#get()}, note that this method assume the result is {@link Term}
   */
  static @NotNull FreeJavaExpr getThunk(@NotNull FreeExprBuilder builder, @NotNull FreeJavaExpr thunkExpr) {
    return builder.checkcast(builder.invoke(Constants.THUNK, thunkExpr, ImmutableSeq.empty()), Term.class);
  }
}
