// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.resolve.salt;

import kala.collection.immutable.ImmutableSeq;
import kala.value.primitive.MutableBooleanValue;
import org.aya.generic.Constants;
import org.aya.generic.term.SortKind;
import org.aya.resolve.ResolveInfo;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.Arg;
import org.aya.util.error.Panic;
import org.aya.util.error.PosedUnaryOperator;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Desugar, but the sugars are not sweet enough, therefore called salt. */
public final class Desalt implements PosedUnaryOperator<Expr> {
  private final @NotNull ResolveInfo info;
  public Desalt(@NotNull ResolveInfo info) { this.info = info; }
  private @Nullable Integer levelVar(@NotNull WithPos<Expr> expr) {
    return switch (expr.data()) {
      case Expr.BinOpSeq _ -> levelVar(expr.descent(this));
      case Expr.LitInt i -> i.integer();
      default -> null;
    };
  }

  @Override public @NotNull Expr apply(@NotNull SourcePos sourcePos, @NotNull Expr expr) {
    switch (expr) {
      case Expr.App(var f, var args) -> {
        if (f.data() instanceof Expr.RawSort(SortKind kind) && kind != SortKind.ISet) {
          if (args.sizeEquals(1)) {
            var arg = args.getFirst();
            if (arg.explicit() && arg.name() == null) {
              // in case of [Type {foo = 0}], report at tyck stage
              var level = levelVar(new WithPos<>(sourcePos, arg.arg().data()));
              if (level != null) return switch (kind) {
                case Type, Set -> new Expr.Sort(kind, level);
                default -> Panic.unreachable();
              };
            }
          }
        }
      }
      case Expr.Match match -> {
        return match.update(
          match.discriminant().map(e -> e.descent(this)),
          match.clauses().map(clause -> clause.descent(this, pattern)),
          match.returns() != null ? match.returns().descent(this) : null
        );
      }
      default -> {
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
      case Expr.RawSort(var kind) -> new Expr.Sort(kind, 0);
      case Expr.LetOpen letOpen -> apply(letOpen.body());
      case Expr.IrrefutableLam lam -> {
        var isVanilla = lam.patterns().allMatch(x -> x.term().data() instanceof Pattern.Refutable);

        ImmutableSeq<LocalVar> lamTele;
        WithPos<Expr> realBody;

        if (isVanilla) {
          // fn a _ c => ...
          lamTele = lam.patterns().map(x -> ((Pattern.Refutable) x.term().data()).toLocalVar(x.term().sourcePos()));
          realBody = lam.body();
        } else {
          lamTele = lam.patterns().mapIndexed((idx, pat) -> {
            if (pat.term().data() instanceof Pattern.Refutable refutable) {
              var bind = refutable.toLocalVar(pat.term().sourcePos());
              // we need fresh bind, since [bind] may be used in the body.
              return LocalVar.generate(bind.name(), bind.definition());
            } else {
              return LocalVar.generate("IrrefutableLam" + idx, pat.term().sourcePos());
            }
          });

          // fn a' _ c' => match a', _, c' { a, _, (con c) => ... }
          // the var with prime are renamed vars

          realBody = new WithPos<>(sourcePos, new Expr.Match(
            lamTele.map(x -> new WithPos<>(x.definition(), new Expr.Ref(x))),
            ImmutableSeq.of(lam.clause()),
            ImmutableSeq.empty(),
            false,
            null
          ));
        }

        yield apply(Expr.buildLam(sourcePos, lamTele.view(), realBody));
      }
    };
  }
  public @NotNull PosedUnaryOperator<Pattern> pattern = new Pat();
  private class Pat implements PosedUnaryOperator<Pattern> {
    @Override public Pattern apply(SourcePos sourcePos, Pattern pattern) {
      return switch (pattern) {
        case Pattern.BinOpSeq binOpSeq -> apply(new PatternBinParser(info, binOpSeq.seq().view()).build(sourcePos));
        default -> pattern.descent(this);
      };
    }
  }
}
