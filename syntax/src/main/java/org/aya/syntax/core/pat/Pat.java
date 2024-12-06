// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.pat;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.control.Option;
import kala.function.IndexedFunction;
import kala.value.MutableValue;
import kala.value.primitive.MutableIntValue;
import org.aya.generic.AyaDocile;
import org.aya.generic.stmt.Shaped;
import org.aya.syntax.core.RichParam;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.ConCallLike;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.ref.GenerateKind;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.Pair;
import org.aya.util.error.Panic;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;

/**
 * Patterns in the core syntax.
 *
 * @author kiva, ice1000, HoshinoTented
 */
@Debug.Renderer(text = "PatToTerm.visit(this).debuggerOnlyToString()")
public sealed interface Pat {
  default @NotNull Pat descentTerm(@NotNull UnaryOperator<Term> op) { return descentTerm((_, t) -> op.apply(t)); }
  default @NotNull Pat descentTerm(@NotNull IndexedFunction<Term, Term> op) { return descentTerm(op, MutableIntValue.create(0)); }
  @NotNull Pat descentPat(@NotNull UnaryOperator<Pat> op);
  @NotNull Pat descentTerm(@NotNull IndexedFunction<Term, Term> op, MutableIntValue bindCount);

  /**
   * The order of bindings should be postorder, that is, {@code (Con0 a (Con1 b)) as c} should be {@code [a , b , c]}
   */
  void consumeBindings(@NotNull BiConsumer<LocalVar, Term> consumer);

  /**
   * Bind the types in {@link Pat}s
   *
   * @see Pat.Bind
   * @see Pat.Con
   */
  @NotNull Pat bind(MutableList<LocalVar> vars);

  /**
   * Traversal the patterns, collect and bind free variables.
   *
   * @param pats the patterns
   * @return (free variables, bound patterns)
   */
  static @NotNull Pair<MutableList<LocalVar>, ImmutableSeq<Pat>>
  collectVariables(@NotNull SeqView<Pat> pats) {
    var buffer = MutableList.<LocalVar>create();
    var newPats = pats.map(p -> p.bind(buffer)).toImmutableSeq();
    return new Pair<>(buffer, newPats);
  }

  static <U> @NotNull MutableList<U> collectBindings(@NotNull SeqView<Pat> pats, @NotNull BiFunction<LocalVar, Term, U> collector) {
    var buffer = MutableList.<U>create();
    pats.forEach(p -> p.consumeBindings((var, type) -> buffer.append(collector.apply(var, type))));
    return buffer;
  }

  static @NotNull MutableList<Param> collectBindings(@NotNull SeqView<Pat> pats) {
    return collectBindings(pats, (v, t) -> new Param(v.name(), t, true));
  }

  static @NotNull MutableList<RichParam> collectRichBindings(@NotNull SeqView<Pat> pats) {
    return collectBindings(pats, RichParam::ofExplicit);
  }

  /**
   * Replace {@link Pat.Meta} with {@link Pat.Meta#solution} (if there is) or {@link Pat.Bind}
   */
  @NotNull Pat inline(@NotNull BiConsumer<LocalVar, Term> bind);

  enum Misc implements Pat {
    Absurd,
    UntypedBind;

    @Override public void consumeBindings(@NotNull BiConsumer<LocalVar, Term> consumer) { }
    @Override public @NotNull Pat bind(MutableList<LocalVar> vars) { return this; }
    @Override public @NotNull Pat inline(@NotNull BiConsumer<LocalVar, Term> bind) { return this; }
    @Override public @NotNull Pat descentPat(@NotNull UnaryOperator<Pat> op) { return this; }
    @Override public @NotNull Pat descentTerm(@NotNull IndexedFunction<Term, Term> op, MutableIntValue bindCount) {
      return this;
    }
  }

  /**
   * @param type of this bind, note that the type may refer to former binds (i.e. dependent type)
   *             by free {@link org.aya.syntax.core.term.LocalTerm}
   */
  record Bind(@NotNull LocalVar bind, @NotNull Term type) implements Pat {
    public @NotNull Bind update(@NotNull Term type) {
      return this.type == type ? this : new Bind(bind, type);
    }

    @Override public void consumeBindings(@NotNull BiConsumer<LocalVar, Term> consumer) {
      consumer.accept(bind, type);
    }

    @Override public @NotNull Pat bind(MutableList<LocalVar> vars) {
      var newType = type.bindTele(vars.view());
      vars.append(bind);
      return update(newType);
    }

    @Override public @NotNull Pat inline(@NotNull BiConsumer<LocalVar, Term> bind) { return this; }
    @Override public @NotNull Pat descentPat(@NotNull UnaryOperator<Pat> op) { return this; }

    @Override
    public @NotNull Pat descentTerm(@NotNull IndexedFunction<Term, Term> op, MutableIntValue bindCount) {
      var result = update(op.apply(bindCount.get(), type));
      bindCount.add(1);
      return result;
    }
  }

  record Tuple(@NotNull Pat lhs, @NotNull Pat rhs) implements Pat {
    public @NotNull Tuple update(@NotNull Pat lhs, @NotNull Pat rhs) {
      return this.lhs == lhs && this.rhs == rhs ? this : new Tuple(lhs, rhs);
    }

    @Override public @NotNull Pat descentPat(@NotNull UnaryOperator<Pat> op) {
      return update(op.apply(lhs), op.apply(rhs));
    }

    @Override public @NotNull Pat descentTerm(@NotNull IndexedFunction<Term, Term> op, MutableIntValue bindCount) {
      var lhsResult = lhs.descentTerm(op, bindCount);
      var rhsResult = rhs.descentTerm(op, bindCount);
      // order is required
      return update(lhsResult, rhsResult);
    }

    @Override public void consumeBindings(@NotNull BiConsumer<LocalVar, Term> consumer) {
      lhs.consumeBindings(consumer);
      rhs.consumeBindings(consumer);
    }

    @Override public @NotNull Pat bind(MutableList<LocalVar> vars) {
      return update(lhs.bind(vars), rhs.bind(vars));
    }

    @Override public @NotNull Pat inline(@NotNull BiConsumer<LocalVar, Term> bind) {
      return update(lhs.inline(bind), rhs.inline(bind));
    }
  }

  record Con(
    @NotNull ConDefLike ref,
    @Override @NotNull ImmutableSeq<Pat> args,
    @NotNull ConCallLike.Head head
  ) implements Pat {
    public @NotNull Con update(@NotNull ImmutableSeq<Pat> args, @NotNull ConCallLike.Head head) {
      return this.args.sameElements(args, true) && head == this.head
        ? this : new Con(ref, args, head);
    }

    @Override public @NotNull Pat descentPat(@NotNull UnaryOperator<Pat> op) {
      return update(args.map(op), head);
    }

    @Override public @NotNull Pat descentTerm(@NotNull IndexedFunction<Term, Term> op, MutableIntValue bindCount) {
      // head first, head cannot refer to the arguments
      var headResult = head.descent((i, t) -> op.apply(bindCount.get() + i, t));
      var argResult = args.map(arg -> arg.descentTerm(op, bindCount));

      return update(argResult, headResult);
    }

    @Override public void consumeBindings(@NotNull BiConsumer<LocalVar, Term> consumer) {
      args.forEach(e -> e.consumeBindings(consumer));
    }

    @Override public @NotNull Pat bind(MutableList<LocalVar> vars) {
      var newHead = head.bindTele(vars.view());
      return update(args.map(e -> e.bind(vars)), newHead);
    }

    @Override public @NotNull Pat inline(@NotNull BiConsumer<LocalVar, Term> bind) {
      return update(args.map(x -> x.inline(bind)), head);
    }
  }

  /**
   * Meta for Hole
   *
   * @param solution the solution of this Meta.
   *                 Note that the solution (and its sub pattern) never contains a {@link Pat.Bind}.
   * @param fakeBind is used when inline if there is no solution.
   *                 So don't add this to {@link LocalCtx} too early
   *                 and remember to inline Meta in <code>ClauseTycker.checkLhs</code>
   */
  record Meta(
    @NotNull MutableValue<Pat> solution,
    @NotNull String fakeBind,
    @NotNull Term type,
    @NotNull SourcePos errorReport
  ) implements Pat {
    public @NotNull Meta update(@Nullable Pat solution, @NotNull Term type) {
      return solution == solution().get() && type == type()
        ? this : new Meta(MutableValue.create(solution), fakeBind, type, errorReport);
    }

    @Override public @NotNull Pat descentPat(@NotNull UnaryOperator<Pat> op) {
      var solution = solution().get();
      return solution == null ? this : update(op.apply(solution), type);
    }

    @Override public @NotNull Pat descentTerm(@NotNull IndexedFunction<Term, Term> op, MutableIntValue bindCount) {
      var type = op.apply(bindCount.get(), this.type);

      // TODO: what about the solution? The bindCount may be incorrect if the Meta is not fully solved!
      var solution = this.solution.get();
      if (solution != null) {
        solution = solution.descentTerm(op, bindCount);
      }

      return update(solution, type);
    }

    @Override public void consumeBindings(@NotNull BiConsumer<LocalVar, Term> consumer) {
      // Called after inline
      Panic.unreachable();
    }

    @Override public @NotNull Pat bind(MutableList<LocalVar> vars) {
      // Called after inline
      return Panic.unreachable();
    }

    @Override public @NotNull Pat inline(@NotNull BiConsumer<LocalVar, Term> bind) {
      var solution = this.solution.get();
      if (solution == null) {
        var name = new LocalVar(fakeBind, errorReport, GenerateKind.Basic.Anonymous);
        bind.accept(name, type);
        solution = new Bind(name, type);
        // We need to set solution if no solution
        this.solution.set(solution);
        return solution;
      } else {
        return solution.inline(bind);
      }
    }
  }

  record ShapedInt(
    @Override int repr,
    @NotNull ConDefLike zero,
    @NotNull ConDefLike suc,
    @NotNull DataCall type
  ) implements Pat, Shaped.Nat<Pat> {
    public ShapedInt update(DataCall type) {
      return type == type() ? this : new ShapedInt(repr, zero, suc, type);
    }

    @Override
    public @NotNull Pat descentPat(@NotNull UnaryOperator<Pat> op) {
      return this;
    }

    @Override public @NotNull Pat descentTerm(@NotNull IndexedFunction<Term, Term> op, MutableIntValue bindCount) {
      return update((DataCall) op.apply(bindCount.get(), type));
    }

    @Override public @NotNull Pat inline(@NotNull BiConsumer<LocalVar, Term> bind) {
      // We are no need to inline type here, because the type of Nat doesn't (mustn't) have any type parameter.
      return this;
    }

    @Override public void consumeBindings(@NotNull BiConsumer<LocalVar, Term> consumer) { }
    @Override public @NotNull Pat bind(MutableList<LocalVar> vars) {
      return update((DataCall) type.bindTele(vars.view()));
    }

    @Override public @NotNull Con makeZero() {
      return new Pat.Con(zero, ImmutableSeq.empty(), makeHead(zero));
    }

    @Override public @NotNull Con makeSuc(@NotNull Pat pat) {
      return new Pat.Con(suc, ImmutableSeq.of(pat), makeHead(suc));
    }

    private ConCallLike.@NotNull Head makeHead(@NotNull ConDefLike conRef) {
      return new ConCallLike.Head(conRef, 0, ImmutableSeq.empty());
    }

    @Override public @NotNull ShapedInt destruct(int repr) {
      return new ShapedInt(repr, zero, suc, type);
    }

    public @NotNull Term toTerm() { return new IntegerTerm(repr, zero, suc, type); }
    @Override public @NotNull ShapedInt map(@NotNull IntUnaryOperator f) {
      return new ShapedInt(f.applyAsInt(repr), zero, suc, type);
    }
  }

  /**
   * It's 'pre' because there are also impossible clauses, which are removed after tycking.
   */
  record Preclause<T extends AyaDocile>(
    @NotNull SourcePos sourcePos,
    @NotNull ImmutableSeq<Pat> pats,
    int bindCount, @Nullable WithPos<T> expr
  ) {
    public static @NotNull Option<Term.Matching> lift(@NotNull Preclause<Term> clause) {
      if (clause.expr == null) return Option.none();
      var match = new Term.Matching(clause.sourcePos, clause.pats, clause.bindCount, clause.expr.data());
      return Option.some(match);
    }
  }
}
