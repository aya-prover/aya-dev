// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.SeqLike;
import kala.collection.SeqView;
import kala.value.MutableValue;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.term.Callable;
import org.aya.core.term.RefTerm;
import org.aya.core.term.Term;
import org.aya.ref.AnyVar;
import org.aya.ref.DefVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface TermFolder<R> extends Function<Term, R> {
  @NotNull R init();

  default @NotNull R fold(@NotNull R acc, @NotNull AnyVar var) {
    return acc;
  }

  default @NotNull R fold(@NotNull R acc, @NotNull Pat pat) {
    return switch (pat) {
      case Pat.Ctor ctor -> fold(acc, ctor.ref());
      case Pat.Bind bind -> fold(acc, bind.bind());
      default -> acc;
    };
  }

  default @NotNull R fold(@NotNull R acc, @NotNull Term term) {
    return switch (term) {
      case Callable call -> fold(acc, call.ref());
      case RefTerm ref -> fold(acc, ref.var());
      case RefTerm.Field field -> fold(acc, field.ref());
      default -> acc;
    };
  }

  @Override default @NotNull R apply(@NotNull Term term) {
    var acc = MutableValue.create(init());
    new TermConsumer() {
      @Override public void pre(@NotNull Term term) {
        acc.set(fold(acc.get(), term));
      }
    }.accept(term);
    return acc.get();
  }

  record Usages(@NotNull AnyVar var) implements TermFolder<Integer> {
    @Override public @NotNull Integer init() {
      return 0;
    }

    @Override public @NotNull Integer fold(@NotNull Integer count, @NotNull AnyVar v) {
      return v == var ? count + 1 : count;
    }
  }

  /**
   * @author re-xyr, ice1000
   * @see RefFinder#HEADER_ONLY
   * @see RefFinder#HEADER_AND_BODY
   */
  record RefFinder(boolean withBody) implements
    TermFolder<@NotNull SeqView<Def>> {
    public static final @NotNull TermFolder.RefFinder HEADER_ONLY = new RefFinder(false);
    public static final @NotNull TermFolder.RefFinder HEADER_AND_BODY = new RefFinder(true);

    @Override
    public @NotNull SeqView<Def> init() {
      return SeqView.empty();
    }

    @Override public @NotNull SeqView<Def> fold(@NotNull SeqView<Def> refs, @NotNull AnyVar usage) {
      return usage instanceof DefVar<?, ?> ref && ref.core instanceof Def def ? refs.appended(def) : refs;
    }

    public @NotNull SeqView<Def> apply(@NotNull GenericDef def) {
      return switch (def) {
        case FnDef fn -> tele(fn.telescope)
          .concat(apply(fn.result))
          .concat(withBody
            ? fn.body.fold(this, clauses -> clauses.view().flatMap(this::matchy))
            : SeqView.empty());
        case CtorDef ctor ->
          tele(ctor.selfTele).concat(withBody ? ctor.clauses.termsView().flatMap(this) : SeqView.empty());
        case StructDef struct ->
          tele(struct.telescope).concat(withBody ? struct.fields.flatMap(this::apply) : SeqView.empty());
        case FieldDef field -> tele(field.telescope())
          .concat(field.body.map(this).getOrDefault(SeqView.empty()))
          .concat(apply(field.result));
        case PrimDef prim -> tele(prim.telescope);
        case DataDef data -> tele(data.telescope)
          .concat(apply(data.result))
          .concat(withBody ? data.body.flatMap(this::apply) : SeqView.empty());
        default -> SeqView.empty();
      };
    }

    private SeqView<Def> matchy(@NotNull Term.Matching match) {
      return apply(match.body());
    }

    private SeqView<Def> tele(@NotNull SeqLike<Term.Param> telescope) {
      return telescope.view().map(Term.Param::type).flatMap(this);
    }
  }
}
