// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.covariance;

import org.aya.core.term.*;
import org.aya.generic.util.NormalizeMode;
import org.aya.tyck.tycker.TyckState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed abstract class CovarianceChecker permits ParametersCovarianceChecker, RecursiveDataChecker {
  protected final @NotNull TyckState state;

  public CovarianceChecker(@NotNull TyckState state) {
    this.state = state;
  }

  protected boolean allowData() {
    return true;
  }

  protected abstract boolean checkNonCovariant(@NotNull Term term);

  protected boolean checkOtherwise(@NotNull Term term) {
    return checkNonCovariant(term);
  }

  protected boolean checkLevels(@NotNull SortTerm levels, @Nullable Callable.DefCall defCall) {
    return false;
  }

  protected boolean checkLevels(int levels, @NotNull Callable.DefCall defCall) {
    return false;
  }

  private boolean checkConstructor(@NotNull Term term) {
    return switch (term.normalize(state, NormalizeMode.WHNF)) {
      case LamTerm lam -> checkConstructor(lam.body());
      case TupTerm tup -> {
        for (var item : tup.items()) {
          if (checkConstructor(item.term())) {
            yield true;
          }
        }
        yield false;
      }
      case ConCall conCall -> {
        for (var argument : conCall.conArgs()) {
          if (checkConstructor(argument.term())) {
            yield true;
          }
        }
        yield false;
      }
      case NewTerm newTerm -> {
        for (var field : newTerm.params().keysView()) {
          assert field.concrete.signature() != null;
          if (checkConstructor(field.concrete.signature().result())) {
            yield true;
          }
        }
        yield false;
      }
      case default -> check(term);
    };
  }

  public boolean check(@NotNull Term term) {
    return switch (term.normalize(state, NormalizeMode.WHNF)) {
      case SortTerm sort -> checkLevels(sort, null);
      case PiTerm pi -> check(pi.body());
      case SigmaTerm sigma -> false;
      case DataCall dataCall when allowData() -> {
        if (checkLevels(dataCall.ulift(), dataCall)) {
          yield true;
        }
        int i = 0;
        for (var argument : dataCall.args()) {
          if (dataCall.ref().concrete.isCovariant(i)) {
            if (checkConstructor(argument.term())) {
              yield true;
            }
          } else {
            if (checkNonCovariant(argument.term())) {
              yield true;
            }
          }
          i++;
        }
        yield false;
      }
      case StructCall structCall -> {
        if (checkLevels(structCall.ulift(), structCall)) {
          yield true;
        }
        var structConcrete = structCall.ref().concrete;
        for (var field : structConcrete.fields) {
          if (structConcrete.isCovariantField(field)) {
            assert field.signature != null;
            if (checkConstructor(field.signature.result())) {
              yield true;
            }
          } else {
            if (checkNonCovariant(field.signature.result())) {
              yield true;
            }
          }
        }
        yield false;
      }
      case default -> {
        yield checkOtherwise(term);
      }
    };
  }
}
