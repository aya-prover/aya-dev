// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.covariance;

import org.aya.core.term.*;
import org.aya.generic.util.NormalizeMode;
import org.aya.tyck.tycker.TyckState;
import org.jetbrains.annotations.NotNull;

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

  protected boolean checkLevels(@NotNull SortTerm levels, @NotNull Callable.DefCall defCall) {
    return false;
  }

  protected boolean checkLevels(int levels, @NotNull Callable.DefCall defCall) {
    return false;
  }

  private boolean checkConstructor(@NotNull Term term) {
    term = term.normalize(state, NormalizeMode.WHNF);

    if (term instanceof LamTerm lam) {
      return checkConstructor(lam.body());
    }

    if (term instanceof TupTerm tup) {
      for (var item : tup.items()) {
        if (checkConstructor(item.term())) {
          return true;
        }
      }
      return false;
    }

    if (term instanceof ConCall conCall) {
      for (var argument : conCall.conArgs()) {
        if (checkConstructor(argument.term())) {
          return true;
        }
      }
      return false;
    }

    if (term instanceof NewTerm newTerm) {
      for (var field : newTerm.params().keysView()) {
        assert field.concrete.signature() != null;
        if (checkConstructor(field.concrete.signature().result())) {
          return true;
        }
      }

      return false;
    }

    return check(term);
  }

  public boolean check(@NotNull Term term) {
    term = term.normalize(state, NormalizeMode.WHNF);

    if (term instanceof SortTerm sort) {
      return checkLevels(sort, null);
    }

    if (term instanceof PiTerm pi) {
      return check(pi.body());
    }

    if (term instanceof SigmaTerm sigma) {
      return false;
    }

    if (term instanceof DataCall dataCall && allowData()) {
      if (checkLevels(dataCall.ulift(), dataCall)) {
        return true;
      }
      int i = 0;
      for (var argument : dataCall.args()) {
        if (dataCall.ref().concrete.isCovariant(i)) {
          if (checkConstructor(argument.term())) {
            return true;
          }
        } else {
          if (checkNonCovariant(argument.term())) {
            return true;
          }
        }
        i++;
      }
      return false;
    }

    if (term instanceof StructCall structCall) {
      if (checkLevels(structCall.ulift(), structCall)) {
        return true;
      }
      var structConcrete = structCall.ref().concrete;
      for (var field : structConcrete.fields) {
        if (structConcrete.isCovariantField(field)) {
          assert field.signature != null;
          if (checkConstructor(field.signature.result())) {
            return true;
          }
        } else {
          if (checkNonCovariant(field.signature.result())) {
            return true;
          }
        }
      }
      return false;
    }

    return checkOtherwise(term);
  }
}
