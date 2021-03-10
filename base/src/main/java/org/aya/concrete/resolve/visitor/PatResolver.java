// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.visitor;

import org.aya.api.error.SourcePos;
import org.aya.concrete.Pattern;
import org.aya.concrete.resolve.context.Context;
import org.aya.generic.Atom;
import org.aya.ref.LocalVar;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public final class PatResolver implements
  Pattern.Clause.Visitor<Context, Pattern.Clause>,
  Pattern.Visitor<Context, Tuple2<Context, Pattern>>,
  Atom.Visitor<Pattern, Context, Tuple2<Context, Atom<Pattern>>> {
  public static final @NotNull PatResolver INSTANCE = new PatResolver();

  private PatResolver() {
  }

  @Override public Pattern.Clause visitMatch(Pattern.Clause.@NotNull Match match, Context context) {
    var ctx = new Ref<>(context);
    var pats = match.patterns().map(pat -> subpatterns(ctx, pat));
    return new Pattern.Clause.Match(pats, match.expr().resolve(ctx.value));
  }

  private Pattern subpatterns(Ref<Context> ctx, Pattern pat) {
    var res = pat.accept(this, ctx.value);
    ctx.value = res._1;
    return res._2;
  }

  @Override public Pattern.Clause visitAbsurd(Pattern.Clause.@NotNull Absurd absurd, Context context) {
    return absurd;
  }

  @Contract(value = "_, _ -> fail", pure = true)
  @Override public Tuple2<Context, Pattern> visitAtomic(Pattern.@NotNull Atomic atomic, Context context) {
    var atom = atomic.atom().accept(this, context);
    var sourcePos = atomic.sourcePos();
    var newCtx = bindAs(atomic.as(), atom._1, sourcePos);
    return Tuple.of(newCtx, new Pattern.Atomic(sourcePos, atom._2, atomic.as()));
  }

  private Context bindAs(LocalVar as, Context ctx, SourcePos sourcePos) {
    return as != null ? ctx.bind(as, sourcePos) : ctx;
  }

  @Contract(value = "_, _ -> fail", pure = true)
  @Override public Tuple2<Context, Pattern> visitCtor(Pattern.@NotNull Ctor ctor, Context context) {
    var newCtx = new Ref<>(context);
    var params = ctor.params().map(p -> subpatterns(newCtx, p));
    var sourcePos = ctor.sourcePos();
    return Tuple.of(bindAs(ctor.as(), newCtx.value, sourcePos), new Pattern.Ctor(sourcePos, ctor.name(), params, ctor.as()));
  }

  @Override public Tuple2<Context, Atom<Pattern>> visitTuple(Atom.@NotNull Tuple<Pattern> tuple, Context context) {
    var newCtx = new Ref<>(context);
    var patterns = tuple.patterns().map(p -> subpatterns(newCtx, p));
    return Tuple.of(newCtx.value, new Atom.Tuple<>(tuple.sourcePos(), patterns));
  }

  @Override public Tuple2<Context, Atom<Pattern>> visitBraced(Atom.@NotNull Braced<Pattern> braced, Context context) {
    var newCtx = new Ref<>(context);
    var patterns = braced.patterns().map(p -> subpatterns(newCtx, p));
    return Tuple.of(newCtx.value, new Atom.Braced<>(braced.sourcePos(), patterns));
  }

  @Override public Tuple2<Context, Atom<Pattern>> visitNumber(Atom.@NotNull Number<Pattern> number, Context context) {
    return Tuple.of(context, number);
  }

  @Override public Tuple2<Context, Atom<Pattern>> visitCalmFace(Atom.@NotNull CalmFace<Pattern> f, Context context) {
    return Tuple.of(context, f);
  }

  @Override public Tuple2<Context, Atom<Pattern>> visitBind(Atom.@NotNull Bind<Pattern> bind, Context context) {
    bind.resolved().value = context.getUnqualifiedMaybe(bind.bind().name(), bind.sourcePos());
    return Tuple.of(context.bind(bind.bind(), bind.sourcePos()), bind);
  }
}
