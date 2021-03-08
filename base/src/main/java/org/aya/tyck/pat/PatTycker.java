// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.concrete.Pattern;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.tyck.ExprTycker;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public record PatTycker(@NotNull ExprTycker exprTycker) implements
  Pattern.Clause.Visitor<Def.Signature, Pat.Clause>,
  Pattern.Visitor<Term.Param, Pat> {
  @Override
  public Pat.Clause visitMatch(Pattern.Clause.@NotNull Match match, Def.Signature signature) {
    var sig = new Ref<>(signature);
    var patterns = match.patterns().stream().sequential().map(pat -> {
      var res = pat.accept(this, sig.value.param().first());
      sig.value = sig.value.inst(res.toTerm());
      return res;
    }).collect(Buffer.factory());
    return new Pat.Clause.Match(patterns, exprTycker.checkExpr(match.expr(), sig.value.result()).wellTyped());
  }

  @Override public Pat.Clause visitAbsurd(Pattern.Clause.@NotNull Absurd absurd, Def.Signature signature) {
    return Pat.Clause.Absurd.INSTANCE;
  }

  @Override public Pat visitAtomic(Pattern.@NotNull Atomic atomic, Term.Param param) {
    throw new UnsupportedOperationException();
  }

  @Override public Pat visitCtor(Pattern.@NotNull Ctor ctor, Term.Param param) {
    throw new UnsupportedOperationException();
  }

  @Override public Pat visitUnresolved(Pattern.@NotNull Unresolved unresolved, Term.Param param) {
    throw new UnsupportedOperationException();
  }
}
