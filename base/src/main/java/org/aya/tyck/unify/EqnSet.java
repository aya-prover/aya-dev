// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.unify;

import kala.collection.immutable.ImmutableMap;
import kala.collection.mutable.Buffer;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.Unit;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.HoleVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.NormalizeMode;
import org.aya.core.Meta;
import org.aya.core.term.CallTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.TermConsumer;
import org.aya.core.visitor.VarConsumer;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.level.LevelEqnSet;
import org.aya.util.Ordering;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Currently we only deal with ambiguous equations (so no 'stuck' equations).
 *
 * @author ice1000
 */
public record EqnSet(
  @NotNull Buffer<Eqn> eqns,
  @NotNull Buffer<HoleVar<Meta>> activeMetas
) {
  public EqnSet() {
    this(Buffer.create(), Buffer.create());
  }

  public void addEqn(@NotNull Eqn eqn) {
    eqns.append(eqn);
    var currentActiveMetas = activeMetas.size();
    eqn.accept(new TermConsumer<>() {
      @Override public Unit visitHole(CallTerm.@NotNull Hole term, Unit unit) {
        var ref = term.ref();
        if (ref.core().body == null) activeMetas.append(ref);
        return unit;
      }
    }, Unit.unit());
    assert activeMetas.size() > currentActiveMetas : "Adding a bad equation";
  }

  /**
   * @return true if <code>this</code> is mutated.
   */
  public boolean simplify(@NotNull Reporter reporter, @NotNull LevelEqnSet levelEqns, @Nullable Trace.Builder tracer) {
    var removingMetas = Buffer.<HoleVar<Meta>>of();
    for (var activeMeta : activeMetas) {
      var solution = activeMeta.core().body;
      if (solution != null) {
        eqns.filterInPlace(eqn -> {
          var usageCounter = new VarConsumer.UsageCounter(activeMeta);
          eqn.accept(usageCounter, Unit.unit());
          if (usageCounter.usageCount() > 0) {
            var defEq = new TypedDefEq(eqn.cmp, reporter, levelEqns, this, tracer, eqn.pos);
            defEq.varSubst.putAll(eqn.varSubst);
            defEq.termDefeq.compare(eqn.lhs.normalize(NormalizeMode.WHNF), eqn.rhs.normalize(NormalizeMode.WHNF));
            return false;
          } else return true;
        });
        removingMetas.append(activeMeta);
      }
    }
    activeMetas.filterNotInPlace(removingMetas::contains);
    return removingMetas.isNotEmpty();
  }

  public record Eqn(
    @NotNull Term lhs, @NotNull Term rhs,
    @NotNull Ordering cmp, @NotNull SourcePos pos,
    @NotNull ImmutableMap<@NotNull LocalVar, @NotNull RefTerm> varSubst
  ) {
    public <P, R> Tuple2<R, R> accept(@NotNull Term.Visitor<P, R> visitor, P p) {
      return Tuple.of(lhs.accept(visitor, p), rhs.accept(visitor, p));
    }
  }
}
