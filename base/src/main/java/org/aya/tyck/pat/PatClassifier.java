// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.collection.mutable.MutableList;
import kala.control.Result;
import org.aya.generic.State;
import org.aya.generic.term.DTKind;
import org.aya.pretty.doc.Doc;
import org.aya.states.TyckState;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.pat.PatToTerm;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.ConCall;
import org.aya.syntax.core.term.call.ConCallLike;
import org.aya.syntax.core.term.call.DataCall;
import org.aya.syntax.ref.LocalVar;
import org.aya.tyck.error.ClausesProblem;
import org.aya.tyck.tycker.AbstractTycker;
import org.aya.tyck.tycker.Problematic;
import org.aya.tyck.tycker.Stateful;
import org.aya.util.Pair;
import org.aya.util.position.SourcePos;
import org.aya.util.reporter.Reporter;
import org.aya.util.tyck.pat.ClassifierUtil;
import org.aya.util.tyck.pat.Indexed;
import org.aya.util.tyck.pat.PatClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Collectors;

/// Coverage checking & case tree generation. Part of the code is generalized and moved to [ClassifierUtil],
/// which is reusable. The main subroutine of coverage checking is _splitting_, i.e. look at a list of clauses,
/// group them by those who match the same constructor head, and recurse -- hence the name _classifier_.
///
/// Note that catch-all patterns will be put into all groups, and literals will have their special classification
/// rules -- if you have very large literals, turning them into constructors and classify with the constructors will
/// be very slow. For pure literal pattern matching, we will only split on literals, and ask for a catch-all.
///
/// There are 3 variants of this task:
///
/// * Look at a single pattern and split according to the head. This is [#classify1], and is language-specific
///   (it depends on what type formers does a language have), so cannot be generalized.
/// * Look at a list of patterns and split them monadically. This is [ClassifierUtil#classifyN],
///   which simply calls [#classify1] and flatMap on the results, so it's language-independent, and is generalized.
/// * Look at a pair of patterns and split them. This is [ClassifierUtil#classify2],
///   which is just a special case of [#classifyN], but implemented for convenience of dealing with binary tuples.
public record PatClassifier(
  @NotNull AbstractTycker delegate, @NotNull SourcePos pos
) implements ClassifierUtil<ImmutableSeq<Term>, Term, Param, Pat>, Stateful, Problematic {
  @Override public Param subst(ImmutableSeq<Term> subst, Param param) {
    return param.instTele(subst.view());
  }
  @Override public @NotNull TyckState state() { return delegate.state(); }
  @Override public @NotNull Reporter reporter() { return delegate.reporter(); }
  @Override public Pat normalize(Pat pat) { return pat.inline((_, _) -> { }); }
  @Override public ImmutableSeq<Term> add(ImmutableSeq<Term> subst, Term term) {
    return subst.appended(term);
  }

  /// @param freePats the second layer of [SeqView] should be friendly for random access
  public static @NotNull ImmutableSeq<PatClass.Seq<Term, Pat>> classify(
    @NotNull SeqView<SeqView<Pat>> freePats,
    @NotNull SeqView<Param> telescope, @NotNull AbstractTycker tycker,
    @NotNull SourcePos pos
  ) {
    var classifier = new PatClassifier(tycker, pos);
    var cl = classifier.classifyN(ImmutableSeq.empty(), telescope, freePats
      .mapIndexed((i, clause) -> new Indexed<>(clause, i))
      .toSeq(), 4);
    var p = cl.partition(c -> c.cls().isEmpty());
    var missing = p.component1();
    if (missing.isNotEmpty()) tycker.fail(
      new ClausesProblem.MissingCase(pos, missing));
    return p.component2();
  }

  @Override public @NotNull ImmutableSeq<PatClass.One<Term, Pat>> classify1(
    @NotNull ImmutableSeq<Term> subst, @NotNull Param param,
    @NotNull ImmutableSeq<Indexed<Pat>> clauses, int fuel
  ) {
    var whnfTy = whnf(param.type());
    switch (whnfTy) {
      // Note that we cannot have ill-typed patterns such as constructor patterns under sigma,
      // since patterns here are already well-typed
      case DepTypeTerm(var kind, var lT, var rT) when kind == DTKind.Sigma -> {
        // The type is sigma type, and do we have any non-catchall patterns?
        // In case we do,
        if (clauses.anyMatch(i -> i.pat() instanceof Pat.Tuple)) {
          var matches = clauses.mapIndexedNotNull((i, subPat) -> switch (subPat.pat()) {
            case Pat.Tuple(var l, var r) -> new Indexed<>(new Pair<>(l, r), i);
            case Pat.Bind b -> {
              var name = b.bind().name();
              var ref = LocalVar.generate(name + ".1");
              yield new Indexed<>(new Pair<Pat, Pat>(new Pat.Bind(ref, lT),
                new Pat.Bind(LocalVar.generate(name + ".2"), rT.apply(ref))), i);
            }
            default -> null;
          });
          var classes = classify2(subst, new Param("1", lT, true),
            term -> new Param("2", rT.apply(term), true), matches, fuel);
          // ^ the licit shall not matter
          return classes.map(args -> new PatClass.One<>(new TupTerm(
            args.term1(), args.term2()), new Pat.Tuple(args.pat1(), args.pat2()), args.cls()));
        }
      }
      // THE BIG GAME
      case DataCall dataCall -> {
        // In case clauses are empty, we're just making sure that the type is uninhabited,
        // so proceed as if we have valid patterns
        if (clauses.isNotEmpty() &&
          // there are no clauses starting with a constructor pattern -- we don't need a split!
          clauses.noneMatch(subPat -> subPat.pat() instanceof Pat.Con || subPat.pat() instanceof Pat.ShapedInt)
        ) break;
        var body = dataCall.ref().body();

        // Special optimization for literals
        var lits = clauses.mapNotNull(cl -> cl.pat() instanceof Pat.ShapedInt i ?
          new Indexed<>(i, cl.ix()) : null);
        var binds = Indexed.indices(clauses.filter(cl -> cl.pat() instanceof Pat.Bind));
        if (clauses.isNotEmpty() && lits.size() + binds.size() == clauses.size()) {
          // There is only literals and bind patterns, no constructor patterns
          // So we do not turn them into constructors, but split the literals directly
          var classes = ImmutableSeq.from(lits.collect(
              Collectors.groupingBy(i -> i.pat().repr())).values())
            .map(i -> simple(i.getFirst().pat(),
              Indexed.indices(Seq.wrapJava(i)).concat(binds)));
          return classes.appended(simple(param.toFreshPat(), binds));
        }

        var buffer = MutableList.<PatClass.One<Term, Pat>>create();
        var missedCon = 0;
        // For all constructors,
        for (var con : body) {
          var fuel1 = fuel;
          var conTeleResult = conTele(clauses, dataCall, con);
          if (conTeleResult == null) continue;
          var conTele = conTeleResult.tele;
          // Find all patterns that are either catchall or splitting on this constructor,
          // e.g. for `suc`, `suc (suc a)` will be picked
          var matches = clauses.mapIndexedNotNull((ix, subPat) ->
            // Convert to constructor form
            matches(conTele, con, ix, subPat));
          var conHead = new ConCallLike.Head(con, dataCall.ulift(), conTeleResult.ownerArgs);
          // The only matching cases are catch-all cases, and we skip these
          if (matches.isEmpty()) {
            missedCon++;
            fuel1--;
            // In this case we give up and do not split on this constructor
            if (conTele.isEmpty() || fuel1 <= 0) {
              var err = new ErrorTerm(Doc.plain("..."), false);
              var missingCon = new ConCall(conHead, conTele.isEmpty() ? ImmutableSeq.empty() : ImmutableSeq.of(err));
              buffer.append(new PatClass.One<>(missingCon, Pat.Misc.Absurd, ImmutableIntSeq.empty()));
              continue;
            }
          }
          var classes = classifyN(subst, conTele.view(), matches, fuel1);
          buffer.appendAll(classes.map(args ->
            new PatClass.One<>(new ConCall(conHead, args.term()),
              new Pat.Con(args.pat(), conHead), args.cls())));
        }
        // If we missed all constructors, we combine the cases to a catch-all case
        if (missedCon >= body.size()) {
          return ImmutableSeq.of(simple(param.toFreshPat(), ImmutableIntSeq.empty()));
        }
        return buffer.toSeq();
      }
      default -> { }
    }
    return ImmutableSeq.of(simple(param.toFreshPat(), Indexed.indices(clauses)));
  }

  private static @NotNull PatClass.One<Term, Pat> simple(Pat pat, @NotNull ImmutableIntSeq cls) {
    return new PatClass.One<>(PatToTerm.visit(pat), pat, cls);
  }

  private static @Nullable Indexed<SeqView<Pat>> matches(
    ImmutableSeq<Param> conTele, ConDefLike con, int ix, Indexed<Pat> subPat
  ) {
    return switch (subPat.pat() instanceof Pat.ShapedInt i ? i.constructorForm() : subPat.pat()) {
      case Pat.Con c when c.ref().equals(con) -> new Indexed<>(c.args().view(), ix);
      case Pat.Bind _ -> new Indexed<>(conTele.view().map(Param::toFreshPat), ix);
      default -> null;
    };
  }

  private record ConTele(
    @NotNull ImmutableSeq<Param> tele,
    @NotNull ImmutableSeq<Term> ownerArgs
  ) { }
  private @Nullable ConTele
  conTele(@NotNull ImmutableSeq<? extends Indexed<?>> clauses, DataCall dataCall, ConDefLike con) {
    // Check if this constructor is available by doing the obvious thing
    return switch (PatternTycker.checkAvail(dataCall, con, state())) {
      // If not, check the reason why: it may fail negatively or positively
      case Result.Err(var e) -> {
        // Index unification fails negatively
        if (e == State.Stuck) {
          // If clauses is empty, we continue splitting to see
          // if we can ensure that the other cases are impossible, it would be fine.
          if (clauses.isNotEmpty() &&
            // If clauses has catch-all pattern(s), it would also be fine.
            clauses.noneMatch(seq -> seq.pat() instanceof Pat.Bind)
          ) {
            fail(new ClausesProblem.UnsureCase(pos, con, dataCall));
            yield null;
          }
          yield new ConTele(con.selfTele(ImmutableSeq.empty()), dataCall.args());
        } else yield null;
        // ^ If fails positively, this would be an impossible case
      }
      case Result.Ok(var ok) -> new ConTele(con.selfTele(ok), ok);
    };
  }
}
