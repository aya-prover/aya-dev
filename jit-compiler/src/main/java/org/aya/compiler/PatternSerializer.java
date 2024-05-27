// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.range.primitive.IntRange;
import kala.value.primitive.MutableIntValue;
import org.aya.generic.NameGenerator;
import org.aya.normalize.PatMatcher;
import org.aya.normalize.PatMatcher.State;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.MetaPatTerm;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class PatternSerializer extends AbstractSerializer<ImmutableSeq<PatternSerializer.Matching>> {
  @FunctionalInterface
  public interface SuccessContinuation extends BiConsumer<PatternSerializer, Integer> {
  }

  public record Matching(@NotNull ImmutableSeq<Pat> patterns, @NotNull SuccessContinuation onSucc) {
  }

  public static final @NotNull String VARIABLE_RESULT = "result";
  public static final @NotNull String VARIABLE_STATE = "matchState";
  public static final @NotNull String VARIABLE_SUBSTATE = "subMatchState";

  static final @NotNull String CLASS_META_PAT = getJavaReference(MetaPatTerm.class);
  static final @NotNull String CLASS_PAT_MATCHER = getJavaReference(PatMatcher.class);

  private final @NotNull ImmutableSeq<String> argNames;
  private final @NotNull Consumer<PatternSerializer> onStuck;
  private final @NotNull Consumer<PatternSerializer> onMismatch;
  private int bindCount = 0;
  private final boolean inferMeta;

  public PatternSerializer(
    @NotNull StringBuilder builder,
    int indent,
    @NotNull NameGenerator nameGen,
    @NotNull ImmutableSeq<String> argNames,
    boolean inferMeta,
    @NotNull Consumer<PatternSerializer> onStuck,
    @NotNull Consumer<PatternSerializer> onMismatch
  ) {
    super(builder, indent, nameGen);
    this.argNames = argNames;
    this.inferMeta = inferMeta;
    this.onStuck = onStuck;
    this.onMismatch = onMismatch;
  }

  /// region Serializing

  private void doSerialize(@NotNull Pat pat, @NotNull String term, @NotNull Runnable continuation) {
    switch (pat) {
      case Pat.Absurd _ -> buildIfElse("Panic.unreachable()", State.Stuck, continuation);
      case Pat.Bind _ -> {
        onMatchBind(term);
        continuation.run();
      }
      // TODO: match IntegerTerm / ListTerm first
      case Pat.Con con -> multiStage(con, term, ImmutableSeq.of(
        mTerm -> solveMeta(con, mTerm),
        mTerm -> buildIfInstanceElse(mTerm, CLASS_CONCALLLIKE, State.Stuck, mmTerm ->
          buildIfElse(STR."\{getCallInstance(mmTerm)} == \{getInstance(getReference(con.ref()))}",
            State.Mismatch, () -> {
              var conArgsTerm = buildLocalVar(TYPE_IMMTERMSEQ,
                nameGen.nextName(null), STR."\{mmTerm}.conArgs()");
              doSerialize(con.args().view(), fromSeq(conArgsTerm, con.args().size()).view(),
                () -> buildUpdate(VARIABLE_SUBSTATE, "true"));
            }))
      ), continuation);
      case Pat.Meta _ -> Panic.unreachable();
      case Pat.ShapedInt shapedInt -> multiStage(pat, term, ImmutableSeq.of(
        mTerm -> solveMeta(shapedInt, mTerm),
        mTerm -> matchInt(shapedInt, mTerm),
        mTerm -> doSerialize(shapedInt.constructorForm(), mTerm, continuation)
      ), continuation);
      case Pat.Tuple tuple -> multiStage(tuple, term, ImmutableSeq.of(
        mTerm -> solveMeta(tuple, mTerm),
        mTerm -> buildIfInstanceElse(mTerm, CLASS_TUPLE, State.Stuck, mmTerm ->
          doSerialize(tuple.elements().view(), fromSeq(STR."\{mmTerm}.items()",
            tuple.elements().size()).view(), continuation))
      ), continuation);
    }
  }

  /**
   * Generate multi case matching, these local variable are available:
   * <ul>
   *   <li>{@link #VARIABLE_SUBSTATE}: the state of multi case matching, false means last check failed</li>
   *   <li>{@code tmpName}: this name is generated, they are the first argument of continuation.
   *   {@param preContinuation} may change the term be matched
   *   </li>
   * </ul>
   *
   * @param term            the expression be matched, not always a variable reference
   * @param preContinuation fast path case matching
   * @param continuation    on match success
   */
  private void multiStage(
    @NotNull Pat pat,
    @NotNull String term,
    @NotNull ImmutableSeq<Consumer<String>> preContinuation,
    @NotNull Runnable continuation
  ) {
    var tmpName = nameGen.nextName(null);
    buildUpdate(VARIABLE_SUBSTATE, "false");
    buildLocalVar(CLASS_TERM, tmpName, term);

    for (var pre : preContinuation) {
      buildIf(STR."! \{VARIABLE_SUBSTATE}", () -> {
        pre.accept(tmpName);
      });
    }

    buildIf(VARIABLE_SUBSTATE, continuation);
  }

  private void solveMeta(@NotNull Pat pat, @NotNull String term) {
    if (inferMeta) {
      buildIfInstanceElse(term, CLASS_META_PAT, metaTerm -> {
        buildUpdate(term, STR."\{CLASS_PAT_MATCHER}.realSolution(\{metaTerm})");
        // if the solution is still a meta, we solve it
        // this is a heavy work
        buildIfInstanceElse(term, CLASS_META_PAT, stillMetaTerm -> {
          // TODO: we may store all Pattern in somewhere and refer them by something like `.conArgs().get(114514)`
          var exprializer = new PatternExprializer(nameGen);
          exprializer.serialize(pat);
          var doSolveMetaResult = STR."\{CLASS_PAT_MATCHER}.doSolveMeta(\{exprializer.result()}, \{stillMetaTerm}.meta())";
          appendLine(STR."\{CLASS_SER_UTILS}.copyTo(\{VARIABLE_RESULT}, \{doSolveMetaResult}, \{bindCount});");
          buildUpdate(VARIABLE_SUBSTATE, "true");
          // at this moment, the matching is complete,
          // but we still need to generate the code for normal matching
          // and it will increase bindCount
        }, null);
      }, null);
    }
  }

  private void matchInt(@NotNull Pat.ShapedInt pat, @NotNull String term) {
    buildIfInstanceElse(term, TermExprializer.CLASS_INTEGER, intTerm -> {
      buildIf(STR."\{pat.repr()} == \{intTerm}.repr()", () -> {
        // Pat.ShapedInt provides no binds
        buildUpdate(VARIABLE_SUBSTATE, "true");
      });
    }, null);
  }

  /**
   * @apiNote {@code pats.sizeEquals(terms)}
   */
  private void doSerialize(@NotNull SeqView<Pat> pats, @NotNull SeqView<String> terms, @NotNull Runnable continuation) {
    if (pats.isEmpty()) {
      continuation.run();
      return;
    }

    var pat = pats.getFirst();
    var term = terms.getFirst();
    doSerialize(pat, term, () -> doSerialize(pats.drop(1), terms.drop(1), continuation));
  }

  /// endregion Serializing

  /// region Java Source Code Generate API

  private void buildIfInstanceElse(
    @NotNull String term,
    @NotNull String type,
    @NotNull State state,
    @NotNull Consumer<String> continuation
  ) {
    buildIfInstanceElse(term, type, continuation, () -> updateState(-state.ordinal()));
  }

  private void buildIfElse(@NotNull String condition, @NotNull State state, @NotNull Runnable continuation) {
    buildIfElse(condition, continuation, () -> updateState(-state.ordinal()));
  }

  private void updateState(int state) {
    buildUpdate(VARIABLE_STATE, Integer.toString(state));
  }

  private void onMatchBind(@NotNull String term) {
    appendLine(STR."\{VARIABLE_RESULT}.set(\{bindCount++}, \{term});");
  }

  private int bindAmount(@NotNull Pat pat) {
    var acc = MutableIntValue.create();
    pat.consumeBindings((_, _) -> acc.increment());
    return acc.get();
  }

  /// endregion Java Source Code Generate API

  @Override
  public AyaSerializer<ImmutableSeq<Matching>> serialize(@NotNull ImmutableSeq<Matching> unit) {
    var bindSize = unit.mapToInt(ImmutableIntSeq.factory(),
      x -> x.patterns.view().foldLeft(0, (acc, p) -> acc + bindAmount(p)));
    int maxBindSize = bindSize.max();

    buildLocalVar(STR."\{CLASS_MUTSEQ}<\{CLASS_TERM}>", VARIABLE_RESULT, STR."\{CLASS_MUTSEQ}.fill(\{maxBindSize}, (\{CLASS_TERM}) null)");
    buildLocalVar("int", VARIABLE_STATE, "0");
    buildLocalVar("boolean", VARIABLE_SUBSTATE, "false");

    buildGoto(() -> unit.forEachIndexed((idx, clause) -> {
      var jumpCode = idx + 1;
      bindCount = 0;
      doSerialize(
        clause.patterns().view(),
        argNames.view(),
        () -> updateState(jumpCode));

      buildIf(STR."\{VARIABLE_STATE} > 0", this::buildBreak);
    }));

    // -1 ..= unit.size()
    var range = IntRange.closed(-1, unit.size()).collect(ImmutableSeq.factory());
    buildSwitch(VARIABLE_STATE, range, state -> {
      switch (state) {
        case -1 -> onMismatch.accept(this);
        case 0 -> onStuck.accept(this);
        default -> {
          assert state > 0;
          var realIdx = state - 1;
          unit.get(realIdx).onSucc.accept(this, bindSize.get(realIdx));
        }
      }
    }, () -> buildPanic(null));

    return this;
  }
}
