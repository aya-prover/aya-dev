// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import kala.function.IndexedFunction;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MatchCall;
import org.aya.syntax.ref.ModulePath;
import org.aya.syntax.ref.QName;
import org.aya.syntax.ref.QPath;
import org.aya.syntax.telescope.AbstractTele;
import org.aya.util.binop.Assoc;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

public record Matchy(
  @NotNull Term returnTypeBound,
  @Override @NotNull ModulePath fileModule,
  @Override @NotNull String name,
  @NotNull ImmutableSeq<Term.Matching> clauses
) implements MatchyLike {
  @Override public @NotNull Term type(@NotNull MatchCall data) {
    return returnTypeBound.instTele(data.captures().view().concat(data.args()));
  }

  @Override public @NotNull QName qualifiedName() {
    var module = module();
    return new QName(new QPath(module, module().size()), name());
  }

  public @NotNull Matchy update(@NotNull Term returnTypeBound, @NotNull ImmutableSeq<Term.Matching> clauses) {
    if (this.returnTypeBound == returnTypeBound && this.clauses.sameElements(clauses, true)) return this;
    return new Matchy(returnTypeBound, fileModule, name, clauses);
  }

  public @NotNull Matchy descent(@NotNull IndexedFunction<Term, Term> f) {
    return update(
      returnTypeBound.descent(f),
      clauses.map(x -> x.descent(f)));
  }

  @Override public @NotNull ModulePath module() { return fileModule; }
  @Override public @NotNull AbstractTele signature() { return Panic.unreachable(); }
  @Override public Assoc assoc() { return Panic.unreachable(); }
  @Override public OpInfo opInfo() { return Panic.unreachable(); }
}
