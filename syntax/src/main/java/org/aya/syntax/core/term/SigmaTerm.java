// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.term;

import kala.collection.mutable.MutableList;
import kala.function.IndexedFunction;
import org.aya.generic.Renamer;
import org.aya.generic.term.SortKind;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.marker.Formation;
import org.aya.syntax.core.term.marker.StableWHNF;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.ForLSP;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;

/**
 * @author re-xyr
 */
public record SigmaTerm(@NotNull Term param, @NotNull Closure body) implements StableWHNF, Formation {
  public @NotNull SigmaTerm update(@NotNull Term param, @NotNull Closure body) {
    return param == this.param && body == this.body ? this : new SigmaTerm(param, body);
  }

  @Override public @NotNull Term descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(f.apply(0, param), body.descent(f));
  }

  public static @NotNull SortTerm lub(@NotNull SortTerm x, @NotNull SortTerm y) {
    int lift = Math.max(x.lift(), y.lift());
    return x.kind() == SortKind.Set || y.kind() == SortKind.Set
      ? new SortTerm(SortKind.Set, lift)
      : x.kind() == SortKind.Type || y.kind() == SortKind.Type
        ? new SortTerm(SortKind.Type, lift)
        : x.kind() == SortKind.ISet || y.kind() == SortKind.ISet
          // ice: this is controversial, but I think it's fine.
          // See https://github.com/agda/cubical/pull/910#issuecomment-1233113020
          ? SortTerm.ISet : Panic.unreachable();
  }

  @ForLSP public static @NotNull DepTypeTerm.Unpi
  unpi(@NotNull Term term, @NotNull UnaryOperator<Term> pre, @NotNull Renamer nameGen) {
    var params = MutableList.<Term>create();
    var names = MutableList.<LocalVar>create();
    while (pre.apply(term) instanceof SigmaTerm(var param, var body)) {
      params.append(param);
      var var = nameGen.bindName(param);
      names.append(var);
      term = body.apply(var);
    }

    return new DepTypeTerm.Unpi(params, names, term);
  }
}
