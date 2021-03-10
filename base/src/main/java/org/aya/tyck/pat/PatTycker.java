// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.tyck.pat;

import org.aya.concrete.Pattern;
import org.aya.concrete.visitor.ExprRefSubst;
import org.aya.core.def.DataDef;
import org.aya.core.def.Def;
import org.aya.core.pat.Pat;
import org.aya.core.term.AppTerm;
import org.aya.core.term.Term;
import org.aya.generic.Atom;
import org.aya.ref.LocalVar;
import org.aya.tyck.ExprTycker;
import org.glavo.kala.collection.Seq;
import org.glavo.kala.collection.SeqLike;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.collection.mutable.MutableHashMap;
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
  Pattern.Clause.Visitor<Def.Signature, Pat.Clause>,
  Pattern.Visitor<Term, Pat>,
  Atom.Visitor<Pattern, Tuple2<Term, LocalVar>, Pat> {
  private final @NotNull ExprTycker exprTycker;
  private final @NotNull ExprRefSubst subst = new ExprRefSubst(MutableHashMap.of());
  public Term resultType;

  public PatTycker(@NotNull ExprTycker exprTycker) {
    this.exprTycker = exprTycker;
  }

  @Override public Pat.Clause visitMatch(Pattern.Clause.@NotNull Match match, Def.Signature signature) {
    var sig = new Ref<>(signature);
    subst.map().clear();
    var patterns = visitPatterns(sig, match.patterns());
    var expr = match.expr().accept(subst, Unit.unit());
    var result = exprTycker.checkExpr(expr, sig.value.result());
    resultType = result.type();
    return new Pat.Clause.Match(patterns, result.wellTyped());
  }

  private @NotNull Seq<Pat> visitPatterns(Ref<Def.Signature> sig, SeqLike<Pattern> stream) {
    var results = Buffer.<Pat>of();
    stream.forEach(pat -> {
      var param = sig.value.param().first();
      // TODO[ice]: generate implicit pattern when param's licitness mismatch pattern licitness
      var res = pat.accept(this, param.type());
      sig.value = sig.value.inst(res.toTerm());
      results.append(res);
    });
    return results;
  }

  @Override public Pat.Clause visitAbsurd(Pattern.Clause.@NotNull Absurd absurd, Def.Signature signature) {
    return Pat.Clause.Absurd.INSTANCE;
  }

  @Override public Pat visitAtomic(Pattern.@NotNull Atomic atomic, Term param) {
    return atomic.atom().accept(this, Tuple.of(param, atomic.as()));
  }

  @Override public Pat visitCalmFace(Atom.@NotNull CalmFace<Pattern> face, Tuple2<Term, LocalVar> t) {
    throw new UnsupportedOperationException();
  }

  @Override public Pat visitNumber(Atom.@NotNull Number<Pattern> number, Tuple2<Term, LocalVar> t) {
    throw new UnsupportedOperationException();
  }

  @Override public Pat visitBraced(Atom.@NotNull Braced<Pattern> braced, Tuple2<Term, LocalVar> termLocalVarTuple2) {
    throw new UnsupportedOperationException();
  }

  @Override public Pat visitTuple(Atom.@NotNull Tuple<Pattern> tuple, Tuple2<Term, LocalVar> t) {
    throw new UnsupportedOperationException();
  }

  @Override public Pat visitBind(Atom.@NotNull Bind<Pattern> bind, Tuple2<Term, LocalVar> t) {
    var selected = selectCtor(t._1, bind.bind().name());
    if (selected == null) {
      var atom = new Atom.Bind<Pat>(bind.sourcePos(), bind.bind(), new Ref<>());
      return new Pat.Atomic(atom, t._2, t._1);
    }
    if (!selected.conTelescope().isEmpty()) {
      // TODO: error report: not enough parameters bind
      throw new ExprTycker.TyckerException();
    }
    var value = bind.resolved().value;
    if (value != null) subst.map().put(bind.bind(), value);
    return new Pat.Ctor(selected.ref(), Seq.of(), t._2, t._1);
  }

  @Override public Pat visitCtor(Pattern.@NotNull Ctor ctor, Term param) {
    var realCtor = selectCtor(param, ctor.name());
    if (realCtor == null) throw new ExprTycker.TyckerException();
    var sig = new Ref<>(new Def.Signature(realCtor.conTelescope(), realCtor.result()));
    var patterns = visitPatterns(sig, ctor.params());
    return new Pat.Ctor(realCtor.ref(), patterns, ctor.as(), param);
  }

  private DataDef.@Nullable Ctor selectCtor(Term param, @NotNull String name) {
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
