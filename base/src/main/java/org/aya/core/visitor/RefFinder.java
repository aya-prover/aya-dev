// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import org.aya.core.Matching;
import org.aya.core.def.*;
import org.aya.core.term.Term;
import org.aya.ref.DefVar;
import org.aya.ref.Var;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr, ice1000
 * @see RefFinder#HEADER_ONLY
 * @see RefFinder#HEADER_AND_BODY
 */
public record RefFinder(boolean withBody) implements
  MonoidalVarFolder<@NotNull SeqView<Def>> {
  public static final @NotNull RefFinder HEADER_ONLY = new RefFinder(false);
  public static final @NotNull RefFinder HEADER_AND_BODY = new RefFinder(true);

  @Override public SeqView<Def> var(Var usage) {
    return usage instanceof DefVar<?, ?> ref && ref.core instanceof Def def ? SeqView.of(def) : SeqView.empty();
  }

  public SeqView<Def> apply(@NotNull GenericDef def) {
    return switch (def) {
      case FnDef fn -> tele(fn.telescope)
        .concat(apply(fn.result))
        .concat(withBody
          ? fn.body.fold(this, clauses -> clauses.view().flatMap(this::matchy))
          : SeqView.empty());
      case CtorDef ctor ->
        tele(ctor.selfTele).concat(withBody ? ctor.clauses.flatMap(this::matchy) : SeqView.empty());
      case StructDef struct ->
        tele(struct.telescope).concat(withBody ? struct.fields.flatMap(this::apply) : SeqView.empty());
      case FieldDef field -> tele(field.telescope())
          .concat(field.body.foldLeft(SeqView.empty(), (rs, body) -> apply(body)))
          .concat(apply(field.result))
          .concat(withBody ? field.clauses.flatMap(this::matchy) : SeqView.empty());
      case PrimDef prim -> tele(prim.telescope);
      case DataDef data -> tele(data.telescope)
        .concat(apply(data.result))
        .concat(withBody ? data.body.flatMap(this::apply) : SeqView.empty());
      default -> SeqView.empty();
    };
  }

  private SeqView<Def> matchy(@NotNull Matching match) {
    return apply(match.body());
  }

  private SeqView<Def> tele(SeqLike<Term.Param> telescope) {
    return telescope.view().map(Term.Param::type).flatMap(this);
  }

  @Override public @NotNull SeqView<Def> e() {
    return SeqView.empty();
  }

  @Override public @NotNull SeqView<Def> op(@NotNull SeqView<Def> a, @NotNull SeqView<Def> b) {
    return a.concat(b);
  }

  @Override public @NotNull SeqView<Def> ops(@NotNull SeqLike<SeqView<Def>> as) {
    return as.view().flatMap(a -> a);
  }
}
