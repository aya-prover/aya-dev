// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.concrete.Pattern;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.term.AppTerm;
import org.aya.core.term.Term;
import org.aya.tyck.ExprTycker;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author ice1000
 */
public record PatTycker(@NotNull ExprTycker exprTycker) implements
  Pattern.Clause.Visitor<Def.Signature, Pat.Clause>,
  Pattern.Visitor<Term, Pat> {
  @Override
  public Pat.Clause visitMatch(Pattern.Clause.@NotNull Match match, Def.Signature signature) {
    var sig = new Ref<>(signature);
    var patterns = visitPatterns(sig, match.patterns().stream()).collect(Buffer.factory());
    return new Pat.Clause.Match(patterns, exprTycker.checkExpr(match.expr(), sig.value.result()).wellTyped());
  }

  private Stream<Pat> visitPatterns(Ref<Def.Signature> sig, Stream<Pattern> stream) {
    return stream.sequential().map(pat -> {
      var res = pat.accept(this, sig.value.param().first().type());
      sig.value = sig.value.inst(res.toTerm());
      return res;
    });
  }

  @Override public Pat.Clause visitAbsurd(Pattern.Clause.@NotNull Absurd absurd, Def.Signature signature) {
    return Pat.Clause.Absurd.INSTANCE;
  }

  @Override public Pat visitAtomic(Pattern.@NotNull Atomic atomic, Term param) {
    throw new UnsupportedOperationException();
  }

  @Override public Pat visitCtor(Pattern.@NotNull Ctor ctor, Term param) {
    var realCtor = selectCtor(ctor, param);
    var sig = new Ref<>(new Def.Signature(realCtor.conTelescope(), realCtor.result()));
    var patterns = visitPatterns(sig, ctor.params().stream()).collect(Seq.factory());
    return new Pat.Ctor(realCtor.ref(), patterns, ctor.as(), param);
  }

  private DataDef.Ctor selectCtor(Pattern.@NotNull Ctor ctor, Term param) {
    if (!(param instanceof AppTerm.DataCall dataCall)) {
      // TODO[ice]: report error: splitting on non data
      throw new ExprTycker.TyckerException();
    }
    var core = dataCall.dataRef().core;
    if (core == null) {
      // TODO[ice]: report error: not checked data
      throw new ExprTycker.TyckerException();
    }
    var selected = core.ctors().find(c -> Objects.equals(c.ref().name(), ctor.name()));
    if (selected.isEmpty()) {
      // TODO[ice]: report error: cannot find ctor of name
      throw new ExprTycker.TyckerException();
    }
    return selected.get();
  }
}
