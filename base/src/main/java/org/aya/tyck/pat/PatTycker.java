// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.api.error.IgnoringReporter;
import org.aya.api.error.Reporter;
import org.aya.concrete.Pattern;
import org.aya.concrete.Pattern;
import org.aya.concrete.visitor.ExprRefSubst;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.term.AppTerm;
import org.aya.core.term.Term;
import org.aya.ref.LocalVar;
import org.aya.tyck.ExprTycker;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableHashMap;
import org.glavo.kala.collection.mutable.MutableHashSet;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.tuple.Unit;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author ice1000
 */
public final class PatTycker implements
  Pattern.Clause.Visitor<Def.Signature, Tuple2<@NotNull Term, Pat.Clause>>,
  Pattern.Visitor<Term, Pat> {
  private final @NotNull ExprTycker exprTycker;
  private final @NotNull ExprRefSubst subst;

  public PatTycker(@NotNull ExprTycker exprTycker) {
    this.exprTycker = exprTycker;
    subst = new ExprRefSubst(exprTycker.metaContext.reporter(), MutableHashMap.of(), MutableHashSet.of());
  }

  public @NotNull Tuple2<@NotNull Term, @NotNull ImmutableSeq<Pat.Clause>>
  elabClause(@NotNull ImmutableSeq<Pattern.@NotNull Clause> clauses, Ref<Def.@NotNull Signature> signature) {
    var res = clauses.map(clause -> {
      var elabClause = clause.accept(this, signature.value);
      signature.value = signature.value.mapTerm(elabClause._1);
      return elabClause._2;
    });
    return Tuple.of(signature.value.result(), res);
  }

  @Override
  public Tuple2<@NotNull Term, Pat.Clause> visitMatch(Pattern.Clause.@NotNull Match match, Def.Signature signature) {
    var sig = new Ref<>(signature);
    subst.clear();
    var recover = MutableHashMap.from(exprTycker.localCtx);
    var patterns = visitPatterns(sig, match.patterns());
    var expr = match.expr().accept(subst, Unit.unit());
    var result = exprTycker.checkExpr(expr, sig.value.result());
    exprTycker.localCtx.clear();
    exprTycker.localCtx.putAll(recover);
    return Tuple.of(result.type(), new Pat.Clause.Match(patterns, result.wellTyped()));
  }

  private @NotNull ImmutableSeq<Pat> visitPatterns(Ref<Def.Signature> sig, SeqLike<Pattern> stream) {
    var results = Buffer.<Pat>of();
    stream.forEach(pat -> {
      var param = sig.value.param().first();
      // TODO[ice]: generate implicit pattern when param's licitness mismatch pattern licitness
      var res = pat.accept(this, param.type());
      sig.value = sig.value.inst(res.toTerm());
      results.append(res);
    });
    return results.toImmutableSeq();
  }

  @Override public Tuple2<@NotNull Term, Pat.Clause>
  visitAbsurd(Pattern.Clause.@NotNull Absurd absurd, Def.Signature signature) {
    return Tuple.of(signature.result(), Pat.Clause.Absurd.INSTANCE);
  }

  @Override public Pat visitCalmFace(Pattern.@NotNull CalmFace face, Term t) {
    throw new UnsupportedOperationException();
  }

  @Override public Pat visitNumber(Pattern.@NotNull Number number, Term t) {
    throw new UnsupportedOperationException();
  }

  @Override public Pat visitTuple(Pattern.@NotNull Tuple tuple, Term t) {
    exprTycker.localCtx.put(tuple.as(), t);
    throw new UnsupportedOperationException();
  }

  @Override public Pat visitBind(Pattern.@NotNull Bind bind, Term t) {
    var v = bind.bind();
    var selected = selectCtor(t, v.name(), IgnoringReporter.INSTANCE);
    if (selected == null) {
      exprTycker.localCtx.put(v, t);
      return new Pat.Bind(true, v, t);
    }
    if (!selected.conTelescope().isEmpty()) {
      // TODO: error report: not enough parameters bind
      throw new ExprTycker.TyckerException();
    }
    var value = bind.resolved().value;
    if (value != null) subst.good().put(v, value);
    else subst.bad().add(v);
    return new Pat.Ctor(true, selected.ref(), ImmutableSeq.of(), null, t);
  }

  @Override public Pat visitCtor(Pattern.@NotNull Ctor ctor, Term param) {
    var realCtor = selectCtor(param, ctor.name(), subst.reporter());
    if (realCtor == null) throw new ExprTycker.TyckerException();
    var sig = new Ref<>(new Def.Signature(realCtor.conTelescope(), realCtor.result()));
    var patterns = visitPatterns(sig, ctor.params());
    return new Pat.Ctor(true, realCtor.ref(), patterns, ctor.as(), param);
  }

  private DataDef.@Nullable Ctor selectCtor(Term param, @NotNull String name, @NotNull Reporter reporter) {
    if (!(param instanceof AppTerm.DataCall dataCall)) {
      // TODO[ice]: report error: splitting on non data
      return null;
    }
    var core = dataCall.dataRef().core;
    if (core == null) {
      // TODO[ice]: report error: not checked data
      return null;
    }
    var selected = core.ctors().find(c -> Objects.equals(c.ref().name(), name));
    if (selected.isEmpty()) {
      // TODO[ice]: report error: cannot find ctor of name
      return null;
    }
    return selected.get();
  }
}
