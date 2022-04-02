// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableHashMap;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableTreeMap;
import org.aya.core.term.Term;
import org.aya.distill.BaseDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.ref.Var;
import org.aya.util.distill.AyaDocile;
import org.aya.util.distill.DistillerOptions;
import org.jetbrains.annotations.Debug;
import org.jetbrains.annotations.NotNull;

/**
 * "Substitution object"
 *
 * @author ice1000
 */
@Debug.Renderer(text = "map.toString()",
  childrenArray = "map.asJava().entrySet().toArray()",
  hasChildren = "map.isNotEmpty()")
public record Subst(@NotNull MutableMap<@NotNull Var, @NotNull Term> map) implements AyaDocile {
  public static final @NotNull Subst EMPTY = new Subst(MutableTreeMap.of((o1, o2) -> {
    throw new UnsupportedOperationException("Shall not modify LevelSubst.EMPTY");
  }));

  public Subst(@NotNull Var var, @NotNull Term term) {
    this(MutableHashMap.of(var, term));
  }

  public void subst(@NotNull Subst subst) {
    if (map.isEmpty()) return;
    map.replaceAll((var, term) -> term.subst(subst));
  }

  public ImmutableSeq<Var> overlap(@NotNull Subst subst) {
    if (subst.map.isEmpty() || map.isEmpty()) return ImmutableSeq.empty();
    return map.keysView().filter(subst.map::containsKey).toImmutableSeq();
  }

  public @NotNull Subst addDirectly(@NotNull Var var, @NotNull Term term) {
    map.put(var, term);
    return this;
  }

  public @NotNull Subst add(@NotNull Var var, @NotNull Term term) {
    subst(new Subst(var, term));
    return addDirectly(var, term);
  }

  public @NotNull Subst add(@NotNull Subst subst) {
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
