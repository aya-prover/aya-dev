// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.salt;

import org.aya.generic.term.SortKind;
import org.aya.resolve.ResolveInfo;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.util.error.Panic;
import org.aya.util.error.PosedUnaryOperator;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Desugar, but the sugars are not sweet enough, therefore called salt. */
public record Desalt(@NotNull ResolveInfo info) implements PosedUnaryOperator<Expr> {
  private @Nullable Integer levelVar(@NotNull WithPos<Expr> expr) {
    return switch (expr.data()) {
      case Expr.BinOpSeq _ -> levelVar(expr.descent(this));
      case Expr.LitInt i -> i.integer();
      default -> null;
    };
  }

  @Override public @NotNull Expr apply(@NotNull SourcePos sourcePos, @NotNull Expr expr) {
    if (expr instanceof Expr.App(var f, var args)) {
      if (f.data() instanceof Expr.RawSort typeF && typeF.kind() != SortKind.ISet) {
        if (args.sizeEquals(1)) {
          var arg = args.getFirst();
          if (arg.explicit() && arg.name() == null) {
            // in case of [Type {foo = 0}], report at tyck stage
            var level = levelVar(new WithPos<>(sourcePos, arg.arg().data()));
            if (level != null) {
              return switch (typeF.kind()) {
                case Type -> new Expr.Type(level);
                case Set -> new Expr.Set(level);
                default -> Panic.unreachable();
              };
            }
          }
        }
      }
    }

    if (expr instanceof Expr.Sugar satou) {
      return desugar(sourcePos, satou);
    } else {
      return expr.descent(this);
    }
  }

  public @NotNull Expr desugar(@NotNull SourcePos sourcePos, @NotNull Expr.Sugar satou) {
    return switch (satou) {
      case Expr.BinOpSeq(var seq) -> {
        assert seq.isNotEmpty();
        yield apply(new ExprBinParser(info, seq.view()).build(sourcePos));
      }
      case Expr.Do aDo -> throw new UnsupportedOperationException("TODO");
      case Expr.Idiom idiom -> throw new UnsupportedOperationException("TODO");
      case Expr.RawSort(var kind) -> switch (kind) {
        case Type -> new Expr.Type(0);
        case Set -> new Expr.Set(0);
        case ISet -> Expr.ISet.INSTANCE;
      };
      case Expr.LetOpen letOpen -> apply(letOpen.body());
    };
  }

  public @NotNull PosedUnaryOperator<Pattern> pattern() { return new Pat(); }

  private class Pat implements PosedUnaryOperator<Pattern> {
    @Override public Pattern apply(SourcePos sourcePos, Pattern pattern) {
      return switch (pattern) {
        case Pattern.BinOpSeq binOpSeq -> apply(new PatternBinParser(info, binOpSeq.seq().view()).build(sourcePos));
        default -> pattern.descent(this);
      };
    }
  }
}
