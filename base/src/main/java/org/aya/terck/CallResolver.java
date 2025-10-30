// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.terck;

import kala.collection.Set;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.ImmutableSet;
import kala.value.MutableValue;
import org.aya.normalize.Normalizer;
import org.aya.states.TyckState;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.def.TyckAnyDef;
import org.aya.syntax.core.def.TyckDef;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.Callable;
import org.aya.syntax.core.term.call.ConCall;
import org.aya.syntax.core.term.call.ConCallLike;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.syntax.core.term.xtt.CoeTerm;
import org.aya.syntax.core.term.xtt.PAppTerm;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.terck.CallGraph;
import org.aya.util.terck.CallMatrix;
import org.aya.util.terck.Relation;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Resolve calls and build call graph of recursive functions,
 * after {@link org.aya.tyck.StmtTycker}.
 *
 * @param targets only search calls to those definitions
 * @author kiva
 */
public record CallResolver(
  @Override @NotNull TyckState state,
  @NotNull FnDef caller,
  @NotNull Set<TyckDef> targets,
  @NotNull MutableValue<Term.Matching> currentClause,
  @NotNull CallGraph<Callable.Tele, TyckDef> graph
) implements Stateful, Consumer<Term.Matching> {
  public CallResolver { assert caller.body().isRight(); }
  public CallResolver(
    @NotNull TyckState state, @NotNull FnDef fn,
    @NotNull Set<TyckDef> targets,
    @NotNull CallGraph<Callable.Tele, TyckDef> graph
  ) {
    this(state, fn, targets, MutableValue.create(), graph);
  }

  private void resolveCall(@NotNull Callable.@Closed Tele callable) {
    if (!(callable.ref() instanceof TyckAnyDef<?> calleeDef)) return;
    var callee = calleeDef.core();
    if (!targets.contains(callee)) return;
    var matrix = new CallMatrix<>(callable, caller, callee,
      caller.telescope().size(), callee.telescope().size());
    fillMatrix(callable, matrix);
    graph.put(matrix);
  }

  private void fillMatrix(@Closed @NotNull Callable callable, CallMatrix<?, TyckDef> matrix) {
    var currentPatterns = currentClause.get();
    assert currentPatterns != null;
    currentPatterns.patterns().forEachIndexed((domParamIx, pat) ->
      callable.args().forEachIndexed((int codParamIx, @Closed Term term) -> {
        // term is Closed cause [callable] is Closed.
        var relation = compare(term, pat);
        matrix.set(domParamIx, codParamIx, relation);
      }));
  }

  /** foetus dependencies */
  private @NotNull Relation compare(@Closed @NotNull Term term, @NotNull Pat pat) {
    return switch (pat) {
      case Pat.Con con -> {
        if (term instanceof ConCallLike con2) {
          var ref = con2.ref();
          var conArgs = con2.conArgs();

          if (!ref.equals(con.ref()) || !conArgs.sizeEquals(con.args())) yield Relation.unk();
          var attempt = compareConArgs(conArgs, con);
          // Reduce arguments and compare again. This may cause performance issues (but not observed yet [2022-11-07]),
          // see https://github.com/agda/agda/issues/2403 for more information.
          if (attempt == Relation.unk()) attempt = compareConArgs(conArgs.map(a -> a.descent(this::whnf)), con);

          yield attempt;
        }

        var subCompare = con.args().view().map(sub -> compare(term, sub));
        var attempt = subCompare.anyMatch(r -> r != Relation.unk()) ? Relation.lt() : Relation.unk();
        if (attempt == Relation.unk()) yield switch (whnf(term)) {
          case ConCallLike con2 -> compare(con2, con);
          // This is related to the predicativity issue mentioned in #907
          case PAppTerm papp -> {
            // closed by [papp]
            @Closed var head = papp.fun();
            while (head instanceof PAppTerm papp2) head = papp2.fun();
            yield compare(head, con);
          }
          default -> attempt;
        };

        yield attempt;
      }
      case Pat.Bind bind -> {
        if (term instanceof FreeTerm(var ref))
          yield ref == bind.bind() ? Relation.eq() : Relation.unk();
        if (headOf(term) instanceof FreeTerm(var ref))
          yield ref == bind.bind() ? Relation.lt() : Relation.unk();
        yield Relation.unk();
      }
      case Pat.ShapedInt intPat -> switch (term) {
        // ice: by well-typedness, we don't need to compareShape
        case IntegerTerm intTerm -> Relation.fromCompare(Integer.compare(intTerm.repr(), intPat.repr()));
        case ConCall con -> compare(con, intPat.constructorForm());
        default -> compare(term, intPat.constructorForm());
      };
      default -> Relation.unk();
    };
  }

  private Relation compareConArgs(@NotNull ImmutableSeq<Term> conArgs, @NotNull Pat.Con con) {
    var subCompare = conArgs.zip(con.args(), this::compare);
    return subCompare.foldLeft(Relation.eq(), Relation::mul);
  }

  /** @return the head of application or projection */
  private @NotNull Term headOf(@NotNull Term term) {
    return switch (term) {
      case AppTerm app -> headOf(app.fun());
      case PAppTerm papp -> headOf(papp.fun());
      case ProjTerm proj -> headOf(proj.of());
      // case FieldTerm access -> headOf(access.of());
      default -> term;
    };
  }

  public void check() {
    var clauses = caller.body().getRightValue().matchingsView();
    clauses.forEach(this);
  }

  @Override public void accept(@NotNull Term.Matching matching) {
    this.currentClause.set(matching);
    var vars = Pat.collectVariables(matching.patterns().view()).component1();
    // all binding of body is insted.
    var erase = matching.body().instTeleVar(vars.view());
    @Closed var instedBody = erase;

    visitTerm(instedBody);
    this.currentClause.set(null);
  }

  private void visitTerm(@Closed @NotNull Term term) {
    if (stopOnBinders(term)) return;

    // TODO: Improve error reporting to include the original call
    var normalizer = new Normalizer(state);
    normalizer.opaque = ImmutableSet.from(targets.map(TyckDef::ref));
    term = normalizer.apply(term);
    if (stopOnBinders(term)) return;
    if (term instanceof Callable.Tele call) resolveCall(call);
    term.descent((_, child) -> {
      // FIXME: ?
      visitTerm(child);
      return child;
    });
  }

  /// Special handling of all binding structures
  private boolean stopOnBinders(@Closed @NotNull Term term) {
    switch (term) {
      case LamTerm(var body) -> {
        visitTerm(body.apply(new LocalVar("_")));
        return true;
      }
      case DepTypeTerm(_, var param, var body) -> {
        visitTerm(param);
        visitTerm(body.apply(new LocalVar("_")));
        return true;
      }
      case CoeTerm(var type, var r, var s) -> {
        visitTerm(r);
        visitTerm(s);
        visitTerm(type.apply(new LocalVar("_")));
        return true;
      }
      default -> { }
    }
    return false;
  }
}
