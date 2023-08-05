// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.core.pat.Pat;
import org.aya.core.visitor.Subst;
import org.aya.guest0x0.cubical.Partial;
import org.aya.ref.LocalVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

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
) implements StableWHNF, Formation {
  public @NotNull PathTerm update(@NotNull Term type, @NotNull Partial<Term> partial) {
    return type == type() && partial == partial() ? this : new PathTerm(params, type, partial);
  }

  @Override public @NotNull PathTerm descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(f.apply(type), partial.map(f));
  }

  public @NotNull Term eta(@NotNull Term term) {
    return new PLamTerm(params, applyDimsTo(term)).rename();
  }

  /**
   * Don't use it in type checking, see the remark by Carlo Angiuli:
   * <a href="https://discord.com/channels/767397347218423858/767397347218423861/1050938238581346344">link</a>
   *
   * @return this if {@link #type} is not {@link PathTerm}
   */
  public @NotNull PathTerm flatten() {
    var ty = type;
    var jon = MutableList.from(params);
    var sterling = MutableList.of(partial);
    while (ty instanceof PathTerm path) {
      // ^ This means the faces in sterling are of type `path`
      ty = path.type();
      jon.appendAll(path.params());
      // Apply the dims to the terms in sterling, so their types become ty
      // Be sure to use `amendTerms` instead of `fmap`/`map`
      sterling.replaceAll(p -> PartialTerm.amendTerms(p, path::applyDimsTo));
      sterling.append(path.partial);
    }
    if (ty == type) return this;
    return new PathTerm(jon.toImmutableSeq(), ty, PartialTerm.merge(sterling));
  }

  public @NotNull PiTerm computePi() {
    return (PiTerm) PiTerm.make(computeParams(), type);
  }

  public @NotNull SeqView<Param> computeParams() {
    return params.view().map(IntervalTerm::param);
  }

  public @NotNull Term substType(@NotNull SeqView<Term> dimensions) {
    return type.subst(ImmutableMap.from(params.zipView(dimensions)));
  }

  /**
   * @param pLam a term of "this" type (i.e. a path type)
   * @return pLam applied with {@link #params}
   * @see #etaLam(Term)
   */
  public @NotNull Term applyDimsTo(@NotNull Term pLam) {
    var args = params.view().map(RefTerm::new);
    loop:
    while (true) {
      if (args.isEmpty()) return pLam;
      switch (pLam) {
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
        default -> {
          break loop;
        }
      }
    }
    var newArgs = args.map(x -> new Arg<Term>(x, true)).toImmutableSeq();
    return new PAppTerm(pLam, newArgs, this);
  }

  public @NotNull Term makeApp(@NotNull Term app, @NotNull Arg<Term> arg) {
    return AppTerm.make(etaLam(app), arg);
  }

  /**
   * @param term a term of "this" type (i.e. the path itself)
   * @return eta-expanded term into a lambda, alpha-renamed
   * @see #computePi() for the type of the returned lambda
   */
  public @NotNull Term etaLam(@NotNull Term term) {
    return params.map(x -> new LamTerm.Param(x, true))
      .foldRight(applyDimsTo(term), LamTerm::new).rename();
  }

  public @NotNull Term substBody(ImmutableSeq<LocalVar> newParams) {
    return type.subst(params.zipView(newParams.view().map(RefTerm::new)).toImmutableMap());
  }
}
