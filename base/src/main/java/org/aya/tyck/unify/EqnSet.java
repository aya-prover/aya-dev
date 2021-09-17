// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.unify;

import kala.collection.immutable.ImmutableMap;
import kala.collection.mutable.Buffer;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.Unit;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.HoleVar;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.NormalizeMode;
import org.aya.api.util.WithPos;
import org.aya.core.Meta;
import org.aya.core.term.CallTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.TermConsumer;
import org.aya.core.visitor.VarConsumer;
import org.aya.pretty.doc.Doc;
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
  @NotNull Buffer<WithPos<HoleVar<Meta>>> activeMetas
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
        if (ref.core().body == null) activeMetas.append(new WithPos<>(eqn.pos, ref));
        return unit;
      }
    }, Unit.unit());
    assert activeMetas.size() > currentActiveMetas : "Adding a bad equation";
  }

  /**
   * @return true if <code>this</code> is mutated.
   */
  public boolean simplify(
    @NotNull LevelEqnSet levelEqns, @NotNull Reporter reporter, @Nullable Trace.Builder tracer
  ) {
    var removingMetas = Buffer.<WithPos<HoleVar<Meta>>>create();
    for (var activeMeta : activeMetas) {
      var solution = activeMeta.data().core().body;
      if (solution != null) {
        eqns.filterInPlace(eqn -> {
          var usageCounter = new VarConsumer.UsageCounter(activeMeta.data());
          eqn.accept(usageCounter, Unit.unit());
          if (usageCounter.usageCount() > 0) {
            solveEqn(levelEqns, reporter, tracer, eqn, false);
            return false;
          } else return true;
        });
        removingMetas.append(activeMeta);
      }
    }
    activeMetas.filterNotInPlace(removingMetas::contains);
    return removingMetas.isNotEmpty();
  }

  public void solveEqn(
    @NotNull LevelEqnSet levelEqns, @NotNull Reporter reporter,
    Trace.@Nullable Builder tracer, @NotNull Eqn eqn, boolean allowVague
  ) {
    var defEq = new DefEq(eqn.cmp, reporter, allowVague, levelEqns, this, tracer, eqn.pos);
    defEq.varSubst.putAll(eqn.varSubst);
    defEq.compareUntyped(eqn.lhs.normalize(NormalizeMode.WHNF), eqn.rhs.normalize(NormalizeMode.WHNF));
  }

  public record Eqn(
    @NotNull Term lhs, @NotNull Term rhs,
    @NotNull Ordering cmp, @NotNull SourcePos pos,
    @NotNull ImmutableMap<@NotNull LocalVar, @NotNull RefTerm> varSubst
  ) implements AyaDocile {
    public <P, R> Tuple2<R, R> accept(@NotNull Term.Visitor<P, R> visitor, P p) {
      return Tuple.of(lhs.accept(visitor, p), rhs.accept(visitor, p));
    }

    public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      return Doc.stickySep(lhs.toDoc(options), Doc.symbol(cmp.symbol), rhs.toDoc(options));
    }
  }
}
