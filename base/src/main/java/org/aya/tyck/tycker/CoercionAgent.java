// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableArray;
import kala.collection.mutable.MutableArrayList;
import org.aya.concrete.Expr;
import org.aya.core.term.*;
import org.aya.core.visitor.Subst;
import org.aya.ref.LocalVar;
import org.aya.tyck.Result;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record CoercionAgent(
  UnifiedTycker unifiedTycker,
  SeqView<Coercer> registerCoercers
) {

  static public ImmutableArray<Coercer> DEFAULT_COERCERS = ImmutableArray.of(
    new Coercer.TermToPath(),
    new Coercer.PathToPi()
  );

  static public class CoercionFailure extends Throwable {
    static CoercionFailure INSTANCE = new CoercionFailure();

    @Override
    public Throwable fillInStackTrace() {
      return this;
    }
  }

  private @Nullable Result.Default tryEtaCompatiblePath(Expr loc, Term term, Term lower, PathTerm path) {
    int sizeLimit = path.params().size();
    var list = MutableArrayList.<LocalVar>create(sizeLimit);
    var innerMost = PiTerm.unpiOrPath(lower, term, unifiedTycker::whnf, list, sizeLimit);
    if (!list.sizeEquals(sizeLimit)) return null;
    unifiedTycker.unifyTyReported(path.computePi(), PiTerm.makeIntervals(list, innerMost.type()), loc);
    var checked = unifiedTycker.checkBoundaries(loc, path, new Subst(), LamTerm.makeIntervals(list, innerMost.wellTyped()));
    return lower instanceof PathTerm actualPath
      ? new Result.Default(actualPath.eta(checked.wellTyped()), actualPath)
      : new Result.Default(path.eta(checked.wellTyped()), checked.type());
  }

  public @NotNull Result coerce(Term lowerTerm, Term lowerType, Term upperType, Expr loc) throws CoercionFailure {
    var upperTypeWHNF = unifiedTycker.whnf(upperType);
    var lowerTypeWHNF = unifiedTycker.whnf(lowerType);
    for (Coercer coercer : registerCoercers) {
      if (coercer.applicable(lowerTypeWHNF, upperTypeWHNF)) {
        return coercer.tryCoerce(this, lowerTypeWHNF, lowerTerm, upperTypeWHNF, loc);
      }
    }
    throw CoercionFailure.INSTANCE;
  }

  public @NotNull Result coerceWithFallback(Result lowerResult, Term upperType, Expr loc) {
    var instantiated = unifiedTycker.instImplicits(lowerResult, loc.sourcePos());
    var lowerTerm = instantiated.wellTyped();
    var lowerType = instantiated.type();
    try {
      return coerce(lowerTerm, lowerType, upperType, loc);
    } catch (CoercionFailure ignored) {
      if (unifiedTycker.unifyTyReported(upperType, lowerType, loc)) return instantiated;
      else
        return unifiedTycker.error(lowerTerm.freezeHoles(unifiedTycker.state), upperType.freezeHoles(unifiedTycker.state));
    }
  }

  public sealed interface Coercer {
    boolean applicable(@NotNull Term lowerTypeWHNF, @NotNull Term upperTypeWHNF);
    @NotNull Result tryCoerce(@NotNull CoercionAgent tycker, @NotNull Term lowerTypeWHNF, @NotNull Term lowerTerm, @NotNull Term upperTypeWHNF, Expr location)
      throws CoercionFailure;

    final class TermToPath implements Coercer {

      @Override
      public boolean applicable(@NotNull Term lowerTypeWHNF, @NotNull Term upperTypeWHNF) {
        return upperTypeWHNF instanceof PathTerm;
      }

      @Override
      public @NotNull Result tryCoerce(@NotNull CoercionAgent tycker, @NotNull Term lowerTypeWHNF, @NotNull Term lowerTerm, @NotNull Term upperTypeWHNF, Expr location) throws CoercionFailure {
        if (upperTypeWHNF instanceof PathTerm path) {
          var result = tycker.tryEtaCompatiblePath(location, lowerTerm, lowerTypeWHNF, path);
          if (result != null) {
            return result;
          }
        }
        throw CoercionFailure.INSTANCE;
      }
    }

    final class PathToPi implements Coercer {
      @Override
      public boolean applicable(@NotNull Term lowerTypeWHNF, @NotNull Term upperTypeWHNF) {
        return lowerTypeWHNF instanceof PathTerm && upperTypeWHNF instanceof PiTerm pi && pi.param().explicit() && pi.param().type() == IntervalTerm.INSTANCE;
      }

      @Override
      public @NotNull Result tryCoerce(@NotNull CoercionAgent tycker, @NotNull Term lowerTypeWHNF, @NotNull Term lowerTerm, @NotNull Term upperTypeWHNF, Expr location) throws CoercionFailure {
        if (lowerTypeWHNF instanceof PathTerm lowerPath && upperTypeWHNF instanceof PiTerm upperPi) {
          MutableArrayList<LocalVar> vars = MutableArrayList.create();
          MutableArrayList<Term> refs = MutableArrayList.create();
          int count = 0;
          Term iteratedUpper = upperPi;

          while (count < lowerPath.params().size() && iteratedUpper instanceof PiTerm pi && pi.param().explicit() && pi.param().type() == IntervalTerm.INSTANCE) {
            var argument = new RefTerm(new LocalVar(lowerPath.params().get(count).name()));
            refs.append(argument);
            vars.append(argument.var());
            iteratedUpper = tycker.unifiedTycker.whnf(pi.substBody(argument));
            count += 1;
          }

          ImmutableArray<Arg<Term>> args = ImmutableArray.from(refs.view().map(term -> new Arg<>(term, true)));
          var body = new PAppTerm(lowerTerm, args, lowerPath);
          var inner = tycker.coerceWithFallback(new Result.Default(body, lowerPath.substType(refs.view())), iteratedUpper, location);
          var term = vars.foldRight(inner.wellTyped(), (x, y) -> new LamTerm(new LamTerm.Param(x, true), y));
          return new Result.Default(term, upperTypeWHNF);
        }
        throw CoercionFailure.INSTANCE;
      }
    }
  }
}
