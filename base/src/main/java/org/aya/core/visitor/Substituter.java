// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.Map;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableTreeMap;
import kala.tuple.Unit;
import org.aya.api.distill.AyaDocile;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.ref.Var;
import org.aya.core.sort.LevelSubst;
import org.aya.core.sort.Sort;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.distill.BaseDistiller;
import org.aya.pretty.doc.Doc;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

/**
 * This doesn't substitute references underlying function calls.
 *
 * @author ice1000
 */
public record Substituter(
  @NotNull Map<Var, Term> termSubst,
  @NotNull LevelSubst levelSubst
) implements TermFixpoint<Unit> {
  public Substituter(@NotNull TermSubst termSubst, @NotNull LevelSubst levelSubst) {
    this(termSubst.map, levelSubst);
  }

  @Override public @NotNull Sort visitSort(@NotNull Sort sort, Unit unit) {
    return levelSubst.applyTo(sort);
  }

  @Override public @NotNull Term visitFieldRef(@NotNull RefTerm.Field term, Unit unit) {
    return termSubst.getOption(term.ref()).map(Term::rename).getOrDefault(term);
  }

  @Override public @NotNull Term visitRef(@NotNull RefTerm term, Unit unused) {
    return termSubst.getOption(term.var()).map(Term::rename).getOrElse(() ->
      TermFixpoint.super.visitRef(term, Unit.unit()));
  }

  /**
   * @author ice1000
   */
  @Debug.Renderer(text = "map.toString()",
    childrenArray = "map.asJava().entrySet().toArray()",
    hasChildren = "map.isNotEmpty()")
  public record TermSubst(@NotNull MutableMap<@NotNull Var, @NotNull Term> map) implements AyaDocile {
    public static final @NotNull TermSubst EMPTY = new TermSubst(MutableTreeMap.of((o1, o2) -> {
      throw new UnsupportedOperationException("Shall not modify LevelSubst.EMPTY");
    }));

    public TermSubst(@NotNull Var var, @NotNull Term term) {
      this(MutableHashMap.of(var, term));
    }

    public void subst(@NotNull TermSubst subst) {
      if (map.isEmpty()) return;
      map.replaceAll((var, term) -> term.subst(subst));
    }

    public ImmutableSeq<Var> overlap(@NotNull TermSubst subst) {
      if (subst.map.isEmpty() || map.isEmpty()) return ImmutableSeq.empty();
      return map.keysView().filter(subst.map::containsKey).toImmutableSeq();
    }

    public @NotNull TermSubst addDirectly(@NotNull Var var, @NotNull Term term) {
      map.put(var, term);
      return this;
    }

    public @NotNull TermSubst add(@NotNull Var var, @NotNull Term term) {
      subst(new TermSubst(var, term));
      return addDirectly(var, term);
    }

    public @NotNull TermSubst add(@NotNull TermSubst subst) {
      if (subst.map.isEmpty()) return this;
      subst(subst);
      map.putAll(subst.map);
      return this;
    }

    public void clear() {
      map.clear();
    }

    public boolean isEmpty() {
      return map.isEmpty();
    }

    @Override
    public @NotNull Doc toDoc(@NotNull DistillerOptions options) {
      return Doc.commaList(
        map.view().map((var, term) -> Doc.sep(
          BaseDistiller.varDoc(var),
          Doc.symbol("=>"),
          term.toDoc(options)
        )).toImmutableSeq()
      );
    }
  }
}
