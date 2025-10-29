// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.Map;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.pat.PatToTerm;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.xtt.DimTerm;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public interface DimInPatsPermutation {
  record Replacer(@NotNull Map<LocalVar, DimTerm> let) implements UnaryOperator<Term> {
    @Override public Term apply(Term term) {
      return switch (term) {
        case FreeTerm(var name) when let.containsKey(name) -> let.get(name);
        default -> term.descent(this);
      };
    }
  }
  private static ImmutableSeq<Map<LocalVar, DimTerm>> makeSubsts(SeqView<LocalVar> remainingDims) {
    if (remainingDims.isEmpty()) return ImmutableSeq.of(Map.empty());
    var first = remainingDims.getFirst();
    return makeSubsts(remainingDims.drop(1)).flatMap(rest ->
      ImmutableArray.Unsafe.wrap(DimTerm.values())
        .map(boundary -> rest.updated(first, boundary)));
  }

  record CtxExtractinator(
    @NotNull LocalCtx ctx,
    @NotNull MutableList<Term> subst
  ) {
    private Pat visit(Pat pat) {
      return switch (pat) {
        case Pat.Bind(var bind, var ty) -> {
          var fresh = new FreeTerm(bind);
          var instTy = ty.instTele(subst.view());
          ctx.put(bind, instTy);
          subst.append(fresh);
          yield new Pat.Bind(bind, instTy);
        }
        case Pat.Tuple(var l, var r) -> {
          l = visit(l);
          r = visit(r);
          yield new Pat.Tuple(l, r);
        }
        case Pat.Con con -> {
          var head = con.head().instantiateTele(subst.view());
          var conArgs = visit(con.args().view());
          yield new Pat.Con(conArgs, head);
        }
        case Pat.Misc _, Pat.ShapedInt _ -> pat;
        default -> Panic.unreachable();
      };
    }
    public ImmutableSeq<Pat> visit(@NotNull SeqView<Pat> pats) {
      return pats.map(this::visit).toSeq();
    }
  }

  static void forEach(@NotNull SeqView<Pat> pats, Consumer<ImmutableSeq<Term>> callback) {
    var dims = new Collector(MutableList.create());
    dims.touch(pats);
    var substs = makeSubsts(dims.dims.view());
    if (substs.sizeEquals(1)) return;

    var args = pats.map(PatToTerm::visit).toSeq();
    // Written like this so breakpoint debugging is easy
    substs.forEach(sub -> {
      var replacer = new Replacer(sub);
      callback.accept(args.map(replacer));
    });
  }

  record Collector(@NotNull MutableList<LocalVar> dims) {
    private void touch(SeqView<Pat> pats) {
      pats.forEach(this::touch);
    }
    private void touch(Pat pat) {
      switch (pat) {
        case Pat.Misc _, Pat.Meta _ -> Panic.unreachable();
        case Pat.Con con when con.ref().hasEq() -> {
          touch(con.args().view().dropLast(1));
          dims.append(((Pat.Bind) con.args().getLast()).bind());
        }
        case Pat.Con con -> touch(con.args().view());
        case Pat.Tuple(var l, var r) -> {
          touch(l);
          touch(r);
        }
        default -> { }
      }
    }
  }
}
