// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.SeqLike;
import kala.collection.mutable.MutableList;
import org.jetbrains.annotations.NotNull;

/**
 * @author re-xyr, kiva, ice1000
 */
public record PiTerm(@NotNull Term.Param param, @NotNull Term body) implements FormTerm, StableWHNF {
  public static @NotNull Term unpi(@NotNull Term term, @NotNull MutableList<Param> params) {
    while (term instanceof PiTerm(var param, var body)) {
      params.append(param);
      term = body;
    }
    return term;
  }

  public @NotNull Term substBody(@NotNull Term term) {
    return body.subst(param.ref(), term);
  }

  public @NotNull Term parameters(@NotNull MutableList<@NotNull Param> params) {
    params.append(param);
    var t = body;
    while (t instanceof PiTerm(var p, var b)) {
      params.append(p);
      t = b;
    }
    return t;
  }

  public static @NotNull Term make(@NotNull SeqLike<@NotNull Param> telescope, @NotNull Term body) {
    return telescope.view().foldRight(body, PiTerm::new);
  }
}
