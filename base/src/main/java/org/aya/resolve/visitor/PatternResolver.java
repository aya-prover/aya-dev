// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Option;
import org.aya.generic.stmt.TyckUnit;
import org.aya.syntax.context.Candidate;
import org.aya.resolve.context.Context;
import org.aya.resolve.error.NameProblem;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.concrete.stmt.ModuleName;
import org.aya.syntax.concrete.stmt.decl.DataCon;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.ref.*;
import org.aya.util.Panic;
import org.aya.util.position.PosedUnaryOperator;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class PatternResolver implements PosedUnaryOperator<Pattern> {
  // DIRTY!!
  private @NotNull Context context;
  private final @NotNull ImmutableSeq<LocalVar> mercy;
  private final @NotNull Consumer<TyckUnit> parentAdd;
  private final @NotNull Reporter reporter;

  public PatternResolver(@NotNull Context context, @NotNull ImmutableSeq<LocalVar> mercy, @NotNull Consumer<TyckUnit> parentAdd, @NotNull Reporter reporter) {
    this.context = context;
    this.mercy = mercy;
    this.parentAdd = parentAdd;
    this.reporter = reporter;
  }

  public @NotNull Context context() { return context; }
  public @NotNull Pattern apply(@NotNull SourcePos pos, @NotNull Pattern pat) { return post(pos, pat.descent(this)); }

  public @NotNull Pattern post(@NotNull SourcePos pos, @NotNull Pattern pat) {
    return switch (pat) {
      case Pattern.Bind bind -> {
        // Check whether this {bind} is a Con
        // getUnqualifiedLocalMaybe may fail with error, however, we ignore them
        var conMaybe = context.iterate(ctx ->
          isCon(ctx.getUnqualifiedLocalMaybe(bind.bind().name(), pos, reporter)));
        if (conMaybe != null) {
          // It wants to be a con!
          addReference(conMaybe);
          yield new Pattern.Con(pos, ConDefLike.from(conMaybe));
        }

        // It is not a constructor, it is a bind
        context = context.bind(bind.bind(), this::toWarn, reporter);
        yield bind;
      }
      case Pattern.QualifiedRef qref -> {
        var qid = qref.qualifiedID();
        if (!(qid.component() instanceof ModuleName.Qualified mod))
          throw new Panic("QualifiedRef#qualifiedID should be qualified");
        var conMaybe = context.iterate(ctx ->
          isCon(ctx.getQualifiedLocalMaybe(mod, qid.name(), pos, reporter)));
        if (conMaybe != null) {
          addReference(conMaybe);
          yield new Pattern.Con(pos, ConDefLike.from(conMaybe));
        }

        // reuse try-catch
        // !! No Such Thing !!
        reporter.report(new NameProblem.QualifiedNameNotFoundError(qid.component(), qid.name(), pos));
        yield qref;
      }
      case Pattern.As as -> {
        context = context.bind(as.as(), this::toWarn, reporter);
        yield as;
      }
      default -> pat;
    };
  }

  private boolean toWarn(@Nullable Candidate<AnyVar> var) {
    return var instanceof Candidate.Defined<AnyVar> defined
      && defined.get() instanceof LocalVar local
      && !mercy.contains(local);
  }

  private void addReference(@NotNull AnyDefVar defVar) {
    if (defVar instanceof DefVar<?, ?> fr) parentAdd.accept(fr.concrete);
  }

  private static @Nullable AnyDefVar isCon(@Nullable Option<AnyVar> myMaybe) {
    if (myMaybe == null || myMaybe.isEmpty()) return null;
    return switch (myMaybe.get()) {
      case DefVar<?, ?> def when def.concrete instanceof DataCon -> def;
      case CompiledVar var when var.core() instanceof JitCon -> var;
      case null, default -> null;
    };
  }
}
