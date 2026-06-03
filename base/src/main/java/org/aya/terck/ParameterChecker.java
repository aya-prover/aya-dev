// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.mutable.MutableList;
import org.aya.generic.TermVisitor;
import org.aya.states.TyckState;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.def.AnyDef;
import org.aya.syntax.core.def.DataDef;
import org.aya.syntax.core.def.DataDefLike;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.Callable;
import org.aya.syntax.core.term.marker.TyckInternal;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.GenerateKind;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.telescope.AbstractTele;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/// ParameterChecker collects data parameters those at non-strictly positive position
/// This only applied to non-mutual inductive data type
public record ParameterChecker(
  @NotNull DefVar<?, ?> self,
  @Nullable LocalVar @NotNull [] parameters,
  @Override @NotNull TyckState state
) implements CovarianceChecker {
  public static void check(@NotNull DataDef self, @NotNull TyckState state) {
    var isParameter = new boolean[ self.telescope().size() ];

    for (var i = 0; i < self.telescope().size(); ++ i) {
      for (var con : self.body()) {
        if (con.pats.isEmpty() || con.pats.get(i) instanceof Pat.Bind) {
          isParameter[i] = true;
        } else {
          isParameter[i] = false;
          break;
        }
      }
    }

    var dataParameters = new LocalVar[isParameter.length];
    for (var i = 0; i < isParameter.length; ++ i) {
      if (isParameter[i]) {
        dataParameters[i] = LocalVar.generate(self.telescope().get(i).name());
      }
    }

    var go = true;
    do {
      go = check(self, dataParameters, state);
    } while (go);

    // set covariance

    for (var i = 0; i < dataParameters.length; ++ i) {
      self.covariance()[i] = dataParameters[i] != null;
    }
  }

  /// Calculate the covariance of data parameters of `self`
  ///
  /// @param parameters a list of data parameters, not null if the parameter MAY be covariance, null if the parameter is non covariance.
  /// @return false if all parameters are not covariant
  public static boolean check(@NotNull DataDef self, @Nullable LocalVar[] parameters, @NotNull TyckState state) {
    // ```
    // inductive PM (n : Nat)
    // | n => any
    // | suc m => intro (PM n)
    // ```
    // `(n : Nat)` will not be considered covariant, as the use in `intro (PM n)` is normalized to `intro (PM (suc m))`
    // In fact, it SHOULD NOT be considered covariant, as that parameter is an index.

    int covCount = 0;
    for (var param : parameters) {
      if (param != null) covCount ++;
    }

    for (var con : self.body()) {
      var selfArgs = MutableList.<Term>create();
      if (con.pats.isEmpty()) {
        for (var param : parameters) {
          if (param == null) {
            param = LocalVar.generate("nonCovariant");
          }

          selfArgs.append(new FreeTerm(param));
        }
      } else {
        con.pats.forEachIndexed((idx, pat) -> {
          var dataParam = parameters[idx];
          if (dataParam == null) {
            // this parameter is not covariant or is an index.
            // extract bindings from pattern
            pat.consumeBindings((v, _) -> selfArgs.append(new FreeTerm(v)));
          } else {
            // use data parameter
            selfArgs.append(new FreeTerm(dataParam));
          }
        });
      }

      var checker = new ParameterChecker(self.ref(), parameters, state);
      var tele = AbstractTele.enrich(TyckDef.defSignature(con).inst(selfArgs.toSeq()), GenerateKind.Basic.Tyck);
      for (var param : tele) {
        try {
          checker.check(param.type());
        } catch (NonCovariantException e) {
          return false;
        }

        if (checker.isFixpoint()) {
          return false;
        }
      }

      // TODO: Arend also check the "body" of a constructor here, what is body?
    }

    int newCovCount = 0;
    for (var param : parameters) {
      if (param != null) newCovCount ++;
    }

    // if no parameter is marked as non-covariance in this round, then the checker reaches a fix point
    return covCount != newCovCount;
  }

  public boolean isFixpoint() {
    return Arrays.stream(parameters).allMatch(Objects::isNull);
  }

  @Override
  public boolean isCovariance(@NotNull DataDefLike def, int at) {
    if (AnyDef.toVar(def) == self) {
      return parameters[at] != null;
    }

    return def.isCovariant(at);
  }

  @Override
  public void checkNonCovariance(@Closed @NotNull Term term) throws NonCovariantException {
    new CallVisitor().term(term);

    if (isFixpoint()) throw new NonCovariantException();
  }

  @Override
  public void checkOtherwise(@NotNull Term type) throws NonCovariantException {
    while (true) {
      type = whnf(type);
      switch (type) {
        case AppTerm(var f, var a) -> {
          type = f;
          checkNonCovariance(a);
        }
        case Callable call -> {
          for (var arg : call.args()) {
            checkNonCovariance(arg);
          }

          return;
        }
        case FreeTermLike _ -> {
          return;
        }
        // Should be normalized
        case LetTerm _ -> Panic.unreachable();
        case ErrorTerm _ -> {
          return;
        }
        case TyckInternal _ -> Panic.unreachable();
        case LocalTerm _ -> Panic.unreachable();
        default -> {
          checkNonCovariance(type);
          return;
        }
      }
    }
  }

  /// @see CallResolver.CallVisitor
  private class CallVisitor implements TermVisitor {
    @Override
    public @NotNull Term term(@NotNull Term term) {
      var whnf = whnf(term);
      if (term instanceof FreeTerm(var free)) {
        for (var i = 0; i < parameters.length; ++ i) {
          if (free == parameters[i]) {
            parameters[i] = null;
          }
        }
      }

      whnf.descent(this);
      return term;
    }
    @Override
    public @NotNull Closure closure(@NotNull Closure closure) {
      var applied = closure.apply(new LocalVar("_"));
      term(applied);
      return closure;
    }
  }
}
