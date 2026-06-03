// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.def.DataDefLike;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.ConCall;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.tyck.tycker.Stateful;
import org.jetbrains.annotations.NotNull;

/// [Arend's implementation](https://github.com/JetBrains/Arend/blob/900528c79af435537a1ba612c56ff7c0732ef020/base/src/main/java/org/arend/typechecking/covariance/CovarianceChecker.java)
public interface CovarianceChecker extends Stateful {
  class NonCovariantException extends Exception {}

  boolean isCovariance(@NotNull DataDefLike def, int at);

  /// Check {@param term} at negative position
  ///
  /// @throws NonCovariantException if the check should be halted
  void checkNonCovariance(@Closed @NotNull Term term) throws NonCovariantException;

  /// Check `term` at strictly positive position, unlike [CovarianceChecker#check(Term)] which accept a type,
  /// this function accept any term
  default void checkTerm(@Closed @NotNull Term term) throws NonCovariantException {
    term = whnf(term);

    switch (term) {
      case LamTerm(var body) -> {
        checkTerm(body.apply(new FreeTerm("i")));
      }
      case TupTerm(var l, var r) -> {
        checkTerm(l);
        checkTerm(r);
      }
      case ConCall(var _, var conArgs) -> {
        for (var arg : conArgs) {
          checkTerm(arg);
        }
      }
      case NewTerm(var inner) -> throw new UnsupportedOperationException("TODO");
      default -> check(term);
    }
  }

  /// Check `type` at strictly positive position
  ///
  /// @throws NonCovariantException if the check is halted
  /// @see CovarianceChecker#checkNonCovariance
  default void check(@Closed @NotNull Term type) throws NonCovariantException {
    type = whnf(type);

    switch (type) {
      case DepTypeTerm(var kind, var param, var body) -> {
        switch (kind) {
          case Pi -> {
            // TODO: maybe unpi?
            checkNonCovariance(param);
            check(body.apply(FreeTerm.dummy()));
          }
          case Sigma -> {
            check(param);
            check(body.apply(FreeTerm.dummy()));
          }
        }
      }
      case DataCall(var ref, var _, var args) -> {
        args.forEachIndexedChecked((idx, arg) -> {
          if (isCovariance(ref, idx)) {
            checkTerm(arg);
          } else {
            checkNonCovariance(arg);
          }
        });
      }
      // TODO: check Path
      default -> checkOtherwise(type);
    }
  }

  default void checkOtherwise(@Closed @NotNull Term type) throws NonCovariantException {
    checkNonCovariance(type);
  }
}
