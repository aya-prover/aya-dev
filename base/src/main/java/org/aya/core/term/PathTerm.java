// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.core.visitor.Subst;
import org.aya.guest0x0.cubical.Partial;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * 'Generalized path' syntax.
 *
 * @param params  Dimension parameters, never empty.
 * @param partial Partial element carried by this path.
 * @see org.aya.concrete.Expr.Path
 */
public record PathTerm(
  @NotNull ImmutableSeq<LocalVar> params,
  @NotNull Term type,
  @NotNull Partial<Term> partial
) implements StableWHNF {
  public @NotNull Term eta(@NotNull Term term) {
    return new PLamTerm(params, applyDimsTo(term)).rename();
  }

  public @NotNull PathTerm flatten() {
    var ty = type;
    var pa = MutableList.from(params);
    var par = MutableList.<Partial<Term>>create();
    if (ty instanceof PathTerm path) {
      // ^ This means the faces in par are of type `path`
      pa.appendAll(path.params());
      ty = path.type();
      // Apply the dims to the terms in par, so their types become ty
      par.replaceAll(p -> p.map(path::applyDimsTo));
      par.append(path.partial);
    }
    return new PathTerm(pa.toImmutableSeq(), ty, PartialTerm.merge(par));
  }

  public @NotNull PiTerm computePi() {
    var iTele = params.view().map(x -> new Param(x, IntervalTerm.INSTANCE, true));
    return (PiTerm) PiTerm.make(iTele, type);
  }

  public @NotNull Term substType(@NotNull SeqView<Term> dimensions) {
    return type.subst(params.zipView(dimensions).toImmutableMap());
  }

  public @NotNull Term applyDimsTo(@NotNull Term pLam) {
    var args = params.view().map(RefTerm::new);
    loop:
    while (true) {
      if (args.isEmpty()) return pLam;
      switch (pLam) {
        case default -> {
          break loop;
        }
        case PLamTerm lam -> {
          assert lam.params().sizeLessThanOrEquals(args);
          pLam = lam.body().subst(new Subst(lam.params(), args.take(lam.params().size())));
          args = args.drop(lam.params().size());
        }
        case LamTerm lam -> {
          // TODO: replace with error report¿
          assert lam.param().explicit();
          pLam = AppTerm.make(lam, new Arg<>(args.first(), true));
          args = args.drop(1);
        }
      }
    }
    var newArgs = args.map(x -> new Arg<Term>(x, true)).toImmutableSeq();
    return new PAppTerm(pLam, newArgs, this);
  }

  public @NotNull PathTerm map(@NotNull ImmutableSeq<LocalVar> params, @NotNull Function<Term, Term> mapper) {
    var ty = mapper.apply(type);
    var par = partial.map(mapper);
    if (ty == type && par == partial) return this;
    return new PathTerm(params, ty, par);
  }

  public @NotNull PathTerm map(@NotNull Function<Term, Term> mapper) {
    return map(params, mapper);
  }

  public @NotNull Term makeApp(@NotNull Term app, @NotNull Arg<Term> arg) {
    return AppTerm.make(etaLam(app), arg);
  }

  /** "not really eta". Used together with {@link #computePi()} */
  public @NotNull Term etaLam(@NotNull Term term) {
    return params.map(x -> new Param(x, IntervalTerm.INSTANCE, true))
      .foldRight(applyDimsTo(term), LamTerm::new).rename();
  }
}
