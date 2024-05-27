// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.pat;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.value.MutableValue;
import org.aya.generic.AyaDocile;
import org.aya.generic.stmt.Shaped;
import org.aya.prettier.BasePrettier;
import org.aya.prettier.CorePrettier;
import org.aya.prettier.Tokens;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.ref.GenerateKind;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.error.Panic;
import org.aya.util.error.SourcePos;
import org.aya.util.error.WithPos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;

/**
 * Patterns in the core syntax.
 *
 * @author kiva, ice1000, HoshinoTented
 */
@Debug.Renderer(text = "PatToTerm.visit(this).debuggerOnlyToString()")
public sealed interface Pat extends AyaDocile {
  @NotNull Pat descent(@NotNull UnaryOperator<Pat> patOp, @NotNull UnaryOperator<Term> termOp);

  /**
   * The order of bindings should be postorder, that is, {@code (Con0 a (Con1 b)) as c} should be {@code [a , b , c]}
   */
  void consumeBindings(@NotNull BiConsumer<LocalVar, Term> consumer);

  record CollectBind(LocalVar var, Term type) { }

  static @NotNull ImmutableSeq<CollectBind> collectBindings(@NotNull SeqView<Pat> pats) {
    var buffer = MutableList.<CollectBind>create();
    pats.forEach(p -> p.consumeBindings((var, type) ->
      buffer.append(new CollectBind(var, type))));
    return buffer.toImmutableSeq();
  }

  /**
   * Replace {@link Pat.Meta} with {@link Pat.Meta#solution} (if there is) or {@link Pat.Bind}
   */
  @NotNull Pat inline(@NotNull BiConsumer<LocalVar, Term> bind);

  enum Absurd implements Pat {
    INSTANCE;

    @Override public @NotNull Pat descent(@NotNull UnaryOperator<Pat> patOp, @NotNull UnaryOperator<Term> termOp) {
      return this;
    }

    @Override public void consumeBindings(@NotNull BiConsumer<LocalVar, Term> consumer) { }
    @Override public @NotNull Pat inline(@NotNull BiConsumer<LocalVar, Term> bind) { return this; }
  }

  @Override default @NotNull Doc toDoc(@NotNull PrettierOptions options) {
    return new CorePrettier(options).pat(this, true, BasePrettier.Outer.Free);
  }

  record Bind(@NotNull LocalVar bind, @NotNull Term type) implements Pat {
    public @NotNull Bind update(@NotNull Term type) {
      return this.type == type ? this : new Bind(bind, type);
    }

    @Override public @NotNull Pat descent(@NotNull UnaryOperator<Pat> patOp, @NotNull UnaryOperator<Term> termOp) {
      return update(termOp.apply(type));
    }

    @Override public void consumeBindings(@NotNull BiConsumer<LocalVar, Term> consumer) {
      consumer.accept(bind, type);
    }

    @Override public @NotNull Pat inline(@NotNull BiConsumer<LocalVar, Term> bind) { return this; }
  }

  record Tuple(@NotNull ImmutableSeq<Pat> elements) implements Pat {
    public @NotNull Tuple update(@NotNull ImmutableSeq<Pat> elements) {
      return this.elements.sameElements(elements, true) ? this : new Tuple(elements);
    }

    @Override
    public @NotNull Pat descent(@NotNull UnaryOperator<Pat> patOp, @NotNull UnaryOperator<Term> termOp) {
      return update(elements.map(patOp));
    }

    @Override public void consumeBindings(@NotNull BiConsumer<LocalVar, Term> consumer) {
      elements.forEach(e -> e.consumeBindings(consumer));
    }

    @Override public @NotNull Pat inline(@NotNull BiConsumer<LocalVar, Term> bind) {
      return update(elements.map(x -> x.inline(bind)));
    }
  }

  record Con(
    @NotNull ConDefLike ref,
    @Override @NotNull ImmutableSeq<Pat> args,
    @NotNull DataCall data
  ) implements Pat {
    public @NotNull Con update(@NotNull ImmutableSeq<Pat> args) {
      return this.args.sameElements(args, true) ? this : new Con(ref, args, data);
    }

    @Override public @NotNull Pat descent(@NotNull UnaryOperator<Pat> patOp, @NotNull UnaryOperator<Term> termOp) {
      return update(args.map(patOp));
    }
    @Override public void consumeBindings(@NotNull BiConsumer<LocalVar, Term> consumer) {
      args.forEach(e -> e.consumeBindings(consumer));
    }

    @Override public @NotNull Pat inline(@NotNull BiConsumer<LocalVar, Term> bind) {
      return update(args.map(x -> x.inline(bind)));
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

    @Override public @NotNull Meta descent(@NotNull UnaryOperator<Pat> f, @NotNull UnaryOperator<Term> g) {
      var solution = solution().get();
      return solution == null ? update(null, g.apply(type)) : update(f.apply(solution), g.apply(type));
    }

    @Override public void consumeBindings(@NotNull BiConsumer<LocalVar, Term> consumer) {
      // We should call storeBindings after inline
      Panic.unreachable();
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

    @Override public @NotNull ShapedInt descent(@NotNull UnaryOperator<Pat> f, @NotNull UnaryOperator<Term> g) {
      return update((DataCall) g.apply(type));
    }

    @Override public @NotNull Pat inline(@NotNull BiConsumer<LocalVar, Term> bind) {
      // We are no need to inline type here, because the type of Nat doesn't (mustn't) have any type parameter.
      return this;
    }
    @Override public void consumeBindings(@NotNull BiConsumer<LocalVar, Term> consumer) { }
    @Override public @NotNull Con makeZero() {
      return new Pat.Con(zero, ImmutableSeq.empty(), type);
    }

    @Override public @NotNull Con makeSuc(@NotNull Pat pat) {
      return new Pat.Con(suc, ImmutableSeq.of(pat), type);
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
    @Nullable WithPos<T> expr
  ) implements AyaDocile {
    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      var prettier = new CorePrettier(options);
      var doc = Doc.emptyIf(pats.isEmpty(), () -> Doc.cat(Doc.ONE_WS, Doc.commaList(
        pats.view().map(p -> prettier.pat(p, true, BasePrettier.Outer.Free)))));
      return expr == null ? doc : Doc.sep(doc, Tokens.FN_DEFINED_AS, expr.data().toDoc(options));
    }

    public static @NotNull Preclause<Term> weaken(@NotNull Term.Matching clause) {
      return new Preclause<>(clause.sourcePos(), clause.patterns(), WithPos.dummy(clause.body()));
    }

    public static @Nullable Term.Matching lift(@NotNull Preclause<Term> clause) {
      if (clause.expr == null) return null;
      return new Term.Matching(clause.sourcePos, clause.pats, clause.expr.data());
    }
  }
}
