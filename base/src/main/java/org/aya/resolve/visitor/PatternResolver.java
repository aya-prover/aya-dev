// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.stmt.TyckUnit;
import org.aya.resolve.context.Context;
import org.aya.resolve.error.NameProblem;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.decl.DataCon;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.ref.*;
import org.aya.util.error.Panic;
import org.aya.util.error.PosedUnaryOperator;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class PatternResolver implements PosedUnaryOperator<Pattern> {
  // DIRTY!!
  private @NotNull Context context;
  private final @NotNull ImmutableSeq<LocalVar> mercy;
  private final @NotNull Consumer<TyckUnit> parentAdd;

  public PatternResolver(@NotNull Context context, @NotNull ImmutableSeq<LocalVar> mercy, @NotNull Consumer<TyckUnit> parentAdd) {
    this.context = context;
    this.mercy = mercy;
    this.parentAdd = parentAdd;
  }

  public @NotNull Context context() { return context; }
  public @NotNull Pattern apply(@NotNull SourcePos pos, @NotNull Pattern pat) { return post(pos, pat.descent(this)); }

  public @NotNull Pattern post(@NotNull SourcePos pos, @NotNull Pattern pat) {
    return switch (pat) {
      case Pattern.Bind bind -> {
        // Check whether this {bind} is a Con
        var conMaybe = context.iterate(ctx -> isCon(ctx.getUnqualifiedLocalMaybe(bind.bind().name(), pos)));
        if (conMaybe != null) {
          // It wants to be a con!
          addReference(conMaybe);
          yield new Pattern.Con(pos, ConDefLike.from(conMaybe));
        }

        // It is not a constructor, it is a bind
        context = context.bind(bind.bind(), this::toWarn);
        yield bind;
      }
      case Pattern.QualifiedRef qref -> {
        var qid = qref.qualifiedID();
        if (!(qid.component() instanceof ModuleName.Qualified mod))
          throw new Panic("QualifiedRef#qualifiedID should be qualified");
        var conMaybe = context.iterate(ctx -> isCon(ctx.getQualifiedLocalMaybe(mod, qid.name(), pos)));
        if (conMaybe != null) {
          addReference(conMaybe);
          yield new Pattern.Con(pos, ConDefLike.from(conMaybe));
        }

        // !! No Such Thing !!
        yield context.reportAndThrow(new NameProblem.QualifiedNameNotFoundError(qid.component(), qid.name(), pos));
      }
      case Pattern.As as -> {
        context = context.bind(as.as(), this::toWarn);
        yield as;
      }
      default -> pat;
    };
  }

  private boolean toWarn(@Nullable AnyVar var) {
    return var instanceof LocalVar && !mercy.contains(var);
  }

  private void addReference(@NotNull AnyDefVar defVar) {
    if (defVar instanceof DefVar<?, ?> fr) parentAdd.accept(fr.concrete);
  }

  private static @Nullable AnyDefVar isCon(@Nullable AnyVar myMaybe) {
    return switch (myMaybe) {
      case DefVar<?, ?> def when def.concrete instanceof DataCon -> def;
      case CompiledVar var when var.core() instanceof JitCon -> var;
      case null, default -> null;
    };
  }
}
