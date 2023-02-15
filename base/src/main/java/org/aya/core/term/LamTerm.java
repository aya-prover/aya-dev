// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.Seq;
import kala.collection.SeqLike;
import org.aya.core.UntypedParam;
import org.aya.core.pat.Pat;
import org.aya.ref.LocalVar;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * @author ice1000
 */
public record LamTerm(@NotNull Param param, @NotNull Term body) implements StableWHNF {
  public record Param(@Override @NotNull LocalVar ref, @Override boolean explicit) implements UntypedParam {
  }

  public Term update(@NotNull Term body) {
    return body == body() ? this : new LamTerm(param(), body);
  }

  @Override public @NotNull Term descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(f.apply(body));
  }

  public static @NotNull Param param(@NotNull Term.Param param) {
    return new Param(param.ref(), param.explicit());
  }
  public static @NotNull Param paramRenamed(@NotNull Term.Param param) {
    return new Param(param.renameVar(), param.explicit());
  }

  public static @NotNull Term unwrap(@NotNull Term term, @NotNull Consumer<@NotNull Param> params) {
    while (term instanceof LamTerm lambda) {
      params.accept(lambda.param);
      term = lambda.body;
    }
    return term;
  }

  public static @NotNull Term make(@NotNull SeqLike<@NotNull Param> telescope, @NotNull Term body) {
    return telescope.view().foldRight(body, LamTerm::new);
  }

  public static Term makeIntervals(Seq<LocalVar> list, Term wellTyped) {
    return make(list.view().map(v -> new Param(v, true)), wellTyped);
  }
}
