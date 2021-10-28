// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.unify;

import kala.collection.immutable.ImmutableMap;
import kala.collection.mutable.Buffer;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import kala.tuple.Unit;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.error.SourcePos;
import org.aya.api.ref.LocalVar;
import org.aya.api.util.WithPos;
import org.aya.core.Meta;
import org.aya.core.term.CallTerm;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.core.visitor.TermConsumer;
import org.aya.pretty.doc.Doc;
import org.aya.tyck.TyckState;
import org.aya.util.Ordering;
import org.jetbrains.annotations.NotNull;

/**
 * Currently we only deal with ambiguous equations (so no 'stuck' equations).
 *
 * @author ice1000
 */
public record EqnSet(
  @NotNull Buffer<Eqn> eqns,
  @NotNull Buffer<WithPos<Meta>> activeMetas
) {
  public EqnSet() {
    this(Buffer.create(), Buffer.create());
  }

  public void addEqn(@NotNull TyckState state, @NotNull Eqn eqn) {
    eqns.append(eqn);
    var currentActiveMetas = activeMetas.size();
    eqn.accept(new TermConsumer<>() {
      @Override public Unit visitHole(CallTerm.@NotNull Hole term, Unit unit) {
        var ref = term.ref();
        if (!state.metas().containsKey(ref))
          activeMetas.append(new WithPos<>(eqn.pos, ref));
        return unit;
      }
    }, Unit.unit());
    assert activeMetas.sizeGreaterThan(currentActiveMetas) : "Adding a bad equation";
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
