// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import kala.tuple.Tuple2;
import org.aya.core.term.*;
import org.aya.core.visitor.Subst;
import org.aya.generic.util.NormalizeMode;
import org.aya.util.Arg;
import org.aya.generic.Constants;
import org.aya.ref.AnyVar;
import org.aya.tyck.tycker.TyckState;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000, re-xyr
 * @apiNote The object identity of this class is used at runtime
 * @implNote Do not override equals or hashCode
 */
public final class Meta implements AnyVar {
  public final @NotNull ImmutableSeq<Term.Param> contextTele;
  public final @NotNull ImmutableSeq<Term.Param> telescope;
  public final @NotNull String name;
  public final @Nullable Term result;
  public final @NotNull SourcePos sourcePos;
  /**
   * Usually implies {@link Meta#result} == null,
   * asserts that the current meta is a type meta.
   */
  public final boolean isType;
  public final @NotNull MutableList<Tuple2<Subst, Term>> conditions = MutableList.create();

  public SeqView<Term.Param> fullTelescope() {
    return contextTele.view().concat(telescope);
  }

  public boolean solve(@NotNull TyckState state, @NotNull Term t) {
    if (t.findUsages(this) > 0) return false;
    if (state.metaNotProps().contains(this)) {
      var term = t.normalize(state, NormalizeMode.WHNF);
      if (!(term instanceof ErrorTerm)) {
        if (!(term instanceof SortTerm sort)) throw new IllegalStateException("expected a sort: " + t);
        if (sort.isProp()) throw new IllegalStateException("expected a non-Prop sort"); // TODO: better reporting
      }
    }
    state.metas().put(this, t);
    return true;
  }

  private Meta(
    @NotNull ImmutableSeq<Term.Param> contextTele,
    @NotNull ImmutableSeq<Term.Param> telescope,
    @NotNull String name, @Nullable Term result,
    boolean isType, @NotNull SourcePos sourcePos
  ) {
    this.contextTele = contextTele;
    this.telescope = telescope;
    this.name = name;
    this.result = result;
    this.isType = isType;
    this.sourcePos = sourcePos;
  }

  public static @NotNull Meta from(
    @NotNull ImmutableSeq<Term.Param> contextTele, @NotNull String name,
    @NotNull SourcePos sourcePos
  ) {
    return new Meta(contextTele, ImmutableSeq.empty(), name, null, true, sourcePos);
  }

  public static @NotNull Meta from(
    @NotNull ImmutableSeq<Term.Param> contextTele, @NotNull String name,
    @NotNull Term result, @NotNull SourcePos sourcePos
  ) {
    if (result instanceof PiTerm pi) {
      var buf = MutableList.<Term.Param>create();
      var r = pi.parameters(buf);
      return new Meta(contextTele, buf.toImmutableSeq(), name, r, false, sourcePos);
    } else return new Meta(contextTele, ImmutableSeq.empty(), name, result, false, sourcePos);
  }

  public @NotNull PiTerm asPi(
    @NotNull String domName, @NotNull String codName, boolean explicit,
    @NotNull ImmutableSeq<Arg<Term>> contextArgs
  ) {
    assert telescope.isEmpty();
    var domVar = Meta.from(contextTele, domName, result, sourcePos);
    var codVar = Meta.from(contextTele, codName, result, sourcePos);
    var dom = new MetaTerm(domVar, contextArgs, ImmutableSeq.empty());
    var cod = new MetaTerm(codVar, contextArgs, ImmutableSeq.empty());
    var domParam = new Term.Param(Constants.randomlyNamed(sourcePos), dom, explicit);
    return new PiTerm(domParam, cod);
  }

  @Override public @NotNull String name() {
    return name;
  }
}
