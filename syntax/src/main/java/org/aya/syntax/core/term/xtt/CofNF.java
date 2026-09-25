// Copyright (c) 2020-2026 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term.xtt;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableSeq;
import org.aya.generic.TermVisitor;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface CofNF {
  record Conj(@NotNull ImmutableSeq<OrVar<EqCofTerm>> elements) {
    public Conj(EqCofTerm... elements) {
      this(ImmutableArray.Unsafe.wrap(elements).map(Conc::new));
    }

    public @NotNull CofNF.Conj descent(@NotNull TermVisitor visitor) {
      if (elements().isEmpty()) return this;
      return update(elements().map(e -> e.map(e1 -> e1.descent(visitor))));
    }

    public @NotNull CofNF.Conj update(@NotNull ImmutableSeq<OrVar<EqCofTerm>> elements) {
      return elements.sameElements(elements(), true) ? this : new Conj(elements);
    }
  }

  private static @NotNull Conj andConj(@NotNull OrVar<Conj> lhs, @NotNull OrVar<Conj> rhs) {
    return switch (lhs) {
      case Conc(var value) -> switch (rhs) {
        case Conc(var varue) -> new Conj(value.elements().concat(varue.elements()));
        case IsVar(var var) -> new Conj(value.elements().appended(new IsVar<>(var)));
      };
      case IsVar(var val) -> switch (rhs) {
        case Conc(var varue) -> new Conj(varue.elements().appended(new IsVar<>(val)));
        case IsVar(var var) -> new Conj(ImmutableSeq.of(new IsVar<>(val), new IsVar<>(var)));
      };
    };
  }

  // compute a and b
  private static @NotNull SeqView<OrVar<Conj>> andDisj(@NotNull Disj a, @NotNull Disj b) {
    MutableSeq<OrVar<Conj>> ret = MutableSeq.create(a.elements().size() * b.elements().size());
    var i = 0;
    for (var ae : a.elements())
      for (var be : b.elements()) {
        var conj = andConj(ae, be);
        // simplify singletons
        if (conj.elements().sizeEquals(1) && conj.elements().get(0) instanceof IsVar(var var)) {
          ret.set(i, new IsVar<>(var));
        } else {
          ret.set(i, new Conc<>(conj));
        }
        i++;
      }
    return ret.view();
  }

  static @NotNull OrVar<Disj> or(@NotNull OrVar<Disj> lhs, @NotNull OrVar<Disj> rhs) {
    return normalizeSingleton(helperForAndOr(lhs, rhs, (a, b) -> a.elements().view().concat(b.elements())));
  }

  static @NotNull OrVar<Disj> and(@NotNull OrVar<Disj> lhs, @NotNull OrVar<Disj> rhs) {
    return normalizeSingleton(helperForAndOr(lhs, rhs, CofNF::andDisj));
  }

  private static @NotNull SeqLike<OrVar<Conj>> helperForAndOr(
    @NotNull OrVar<Disj> lhs, @NotNull OrVar<Disj> rhs,
    @NotNull BiFunction<Disj, Disj, SeqView<OrVar<Conj>>> combine
    ) {
    return switch (lhs) {
      case Conc(var value) -> switch (rhs) {
        case Conc(var varue) -> combine.apply(value, varue);
        case IsVar(var var) -> value.elements().view().appended(new IsVar<>(var));
      };
      case IsVar(var val) -> switch (rhs) {
        case Conc(var varue) -> varue.elements().view().appended(new IsVar<>(val));
        case IsVar(var var) -> ImmutableSeq.of(new IsVar<>(val), new IsVar<>(var));
      };
    };
  }

  /// @apiNote Cannot be [SeqView], because ImmSeq.of().view() cannot backwards infer the type of the elements
  /// This is a relatively rare case of an appropriate use of `SeqLike`
  private static @NotNull OrVar<Disj> normalizeSingleton(SeqLike<OrVar<Conj>> elements) {
    if (elements.sizeEquals(1)) {
      return switch (elements.get(0)) {
        case Conc(var value) -> new Conc<>(new Disj(value));
        case IsVar(var var) -> new IsVar<>(var);
      };
    } else {
      return new Conc<>(new Disj(elements.toSeq()));
    }
  }

  record Disj(@NotNull ImmutableSeq<OrVar<Conj>> elements) implements Term {
    public Disj(Conj... elements) {
      this(ImmutableArray.Unsafe.wrap(elements).map(Conc::new));
    }

    public @NotNull CofNF.Disj descent(@NotNull TermVisitor visitor) {
      if (elements().isEmpty()) return this;
      return update(elements().map(e -> e.map(e1 -> e1.descent(visitor))));
    }

    public @NotNull CofNF.Disj update(@NotNull ImmutableSeq<OrVar<Conj>> elements) {
      return elements.sameElements(elements(), true) ? this : new Disj(elements);
    }
  }

  /// lhs = rhs
  record EqCofTerm(@NotNull Term lhs, @NotNull Term rhs) {
    public @NotNull CofNF.EqCofTerm descent(@NotNull TermVisitor visitor) {
      var newLhs = visitor.term(lhs);
      var newRhs = visitor.term(rhs);
      if (newLhs == lhs && newRhs == rhs) return this;
      return new EqCofTerm(newLhs, newRhs);
    }
  }

  sealed interface OrVar<T> {
    @NotNull CofNF.OrVar<T> map(@NotNull Function<T, T> mapper);
  }

  record Conc<T>(T value) implements OrVar<T> {
    @Override public @NotNull Conc<T> map(@NotNull Function<T, T> mapper) {
      var applied = mapper.apply(value);
      if (applied == value) return this;
      return new Conc<>(applied);
    }
  }
  record IsVar<T>(LocalVar var) implements OrVar<T> {
    @Override public @NotNull IsVar<T> map(@NotNull Function<T, T> mapper) {
      return this;
    }
  }
}
