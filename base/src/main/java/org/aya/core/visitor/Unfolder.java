// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.core.visitor;

import kala.collection.SeqLike;
import kala.collection.Set;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import kala.collection.mutable.MutableSet;
import kala.tuple.Unit;
import org.aya.api.ref.Var;
import org.aya.api.util.Arg;
import org.aya.api.util.WithPos;
import org.aya.core.Matching;
import org.aya.core.def.Def;
import org.aya.core.pat.PatMatcher;
import org.aya.core.sort.LevelSubst;
import org.aya.core.sort.Sort;
import org.aya.core.sort.Sort.LvlVar;
import org.aya.core.term.CallTerm;
import org.aya.core.term.IntroTerm;
import org.aya.core.term.Term;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public interface Unfolder<P> extends TermFixpoint<P> {
  @Contract(pure = true) static @NotNull Substituter.TermSubst buildSubst(
    @NotNull SeqLike<Term.@NotNull Param> self,
    @NotNull SeqLike<@NotNull Arg<@NotNull Term>> args
  ) {
    var subst = new Substituter.TermSubst(MutableMap.of());
    self.view().zip(args).forEach(t -> subst.add(t._1.ref(), t._2.term()));
    return subst;
  }

  @Override @NotNull default Term visitConCall(CallTerm.@NotNull Con conCall, P p) {
    var def = conCall.ref().core;
    // Not yet type checked
    if (def == null) return conCall;
    var args = conCall.args().map(arg -> visitArg(arg, p));
    var subst = checkAndBuildSubst(def.telescope(), args);
    var levelParams = Def.defLevels(def.ref());
    var levelArgs = conCall.sortArgs();
    var levelSubst = buildSubst(levelParams, levelArgs);
    var dropped = args.drop(conCall.head().dataArgs().size());
    var volynskaya = tryUnfoldClauses(p, dropped, subst, levelSubst, def.clauses());
    return volynskaya != null ? volynskaya.data() : new CallTerm.Con(conCall.head(), dropped.toImmutableSeq());
  }

  static @NotNull LevelSubst buildSubst(ImmutableSeq<LvlVar> levelParams, ImmutableSeq<Sort.@NotNull CoreLevel> levelArgs) {
    var levelSubst = new LevelSubst.Simple(MutableMap.of());
    assert levelParams.sizeEquals(levelArgs);
    for (var app : levelArgs.zip(levelParams)) levelSubst.solution().put(app._2, app._1);
    return levelSubst;
  }

  @Override default @NotNull Term visitFnCall(@NotNull CallTerm.Fn fnCall, P p) {
    var def = fnCall.ref().core;
    // Not yet type checked
    if (def == null) return fnCall;
    var args = fnCall.args().map(arg -> visitArg(arg, p));
    var subst = checkAndBuildSubst(def.telescope(), args);
    var levelSubst = buildSubst(def.levels(), fnCall.sortArgs());
    var body = def.body;
    if (body.isLeft()) return body.getLeftValue().subst(subst, levelSubst).accept(this, p);
    var volynskaya = tryUnfoldClauses(p, args, subst, levelSubst, body.getRightValue());
    return volynskaya != null ? volynskaya.data() : new CallTerm.Fn(fnCall.ref(), fnCall.sortArgs(), args);
  }
  private @NotNull Substituter.TermSubst
  checkAndBuildSubst(SeqLike<Term.Param> telescope, SeqLike<Arg<Term>> args) {
    // This shouldn't fail
    // Assertions are enabled optionally, so we could perform some somehow-expensive operations
    assert args.sizeEquals(telescope.size());
    assert Term.Param.checkSubst(telescope, args);
    return buildSubst(telescope, args);
  }

  @Override @NotNull default Term visitPrimCall(@NotNull CallTerm.Prim prim, P p) {
    return prim.ref().core.unfold(prim);
  }

  @Override default @NotNull Term visitHole(@NotNull CallTerm.Hole hole, P p) {
    var def = hole.ref().core();
    // Not yet type checked
    var args = hole.fullArgs().view().map(arg -> visitArg(arg, p)).toImmutableSeq();
    var subst = checkAndBuildSubst(def.fullTelescope(), args);
    var body = def.body;
    if (body == null) return hole;
    return body.subst(subst).accept(this, p);
  }

  default @Nullable WithPos<Term> tryUnfoldClauses(
    P p, SeqLike<Arg<Term>> args,
    Substituter.@NotNull TermSubst subst, LevelSubst levelSubst,
    @NotNull ImmutableSeq<Matching> clauses
  ) {
    for (var matchy : clauses) {
      var termSubst = PatMatcher.tryBuildSubstArgs(matchy.patterns(), args);
      if (termSubst != null) {
        subst.add(termSubst);
        var newBody = matchy.body().subst(subst, levelSubst).accept(this, p);
        return new WithPos<>(matchy.sourcePos(), newBody);
      }
    }
    // Unfold failed
    return null;
  }

  default @NotNull Term visitAccess(CallTerm.@NotNull Access term, P p) {
    var nevv = term.of().accept(this, p);
    var field = term.ref();
    var core = field.core;
    if (!(nevv instanceof IntroTerm.New n)) {
      var args = term.args().map(arg -> visitArg(arg, p));
      var fieldSubst = checkAndBuildSubst(core.telescope().view(), args);
      var levelSubst = buildSubst(Def.defLevels(field), term.sortArgs());
      var dropped = args.drop(term.structArgs().size());
        var mischa = tryUnfoldClauses(p, dropped, fieldSubst, levelSubst, core.clauses);
      return mischa != null ? mischa.data() : new CallTerm.Access(nevv, field,
        term.sortArgs(), term.structArgs(), dropped);
    }
    var arguments = Unfolder.buildSubst(core.telescope(), term.args());
    return n.params().get(field).subst(arguments).accept(this, p);
  }

  /**
   * For tactics.
   *
   * @author ice1000
   */
  record Tracked(
    @NotNull Set<@NotNull Var> unfolding,
    @NotNull MutableSet<@NotNull Var> unfolded
  ) implements Unfolder<Unit> {
    @Override public @NotNull Term visitFnCall(CallTerm.@NotNull Fn fnCall, Unit unit) {
      if (!unfolding.contains(fnCall.ref())) return fnCall;
      unfolded.add(fnCall.ref());
      return Unfolder.super.visitFnCall(fnCall, unit);
    }

    @Override public @NotNull Term visitConCall(@NotNull CallTerm.Con conCall, Unit unit) {
      if (!unfolding.contains(conCall.ref())) return conCall;
      unfolded.add(conCall.ref());
      return Unfolder.super.visitConCall(conCall, unit);
    }
  }
}
