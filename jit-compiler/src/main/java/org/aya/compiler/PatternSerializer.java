// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.range.primitive.IntRange;
import kala.value.primitive.MutableIntValue;
import org.aya.generic.State;
import org.aya.normalize.PatMatcher;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.MetaPatTerm;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.aya.compiler.AyaSerializer.*;

/**
 * We do not serialize meta solve, it is annoying
 */
public final class PatternSerializer extends AbstractSerializer<ImmutableSeq<PatternSerializer.Matching>> {
  @FunctionalInterface
  public interface SuccessContinuation extends BiConsumer<PatternSerializer, Integer> {
  }

  public final static class Once implements Runnable {
    public static @NotNull Once of(@NotNull Runnable run) {
      return new Once(run);
    }

    private final @NotNull Runnable run;
    private boolean dirty = false;

    public Once(@NotNull Runnable run) { this.run = run; }

    @Override
    public void run() {
      if (dirty) {
        throw new Panic("Once");
      }

      dirty = true;
      this.run.run();
    }
  }

  public record Matching(@NotNull ImmutableSeq<Pat> patterns, @NotNull SuccessContinuation onSucc) {
  }

  public static final @NotNull String VARIABLE_RESULT = "result";
  public static final @NotNull String VARIABLE_STATE = "matchState";
  public static final @NotNull String VARIABLE_SUBSTATE = "subMatchState";

  static final @NotNull String CLASS_META_PAT = ExprializeUtils.getJavaReference(MetaPatTerm.class);
  static final @NotNull String CLASS_PAT_MATCHER = ExprializeUtils.getJavaReference(PatMatcher.class);

  private final @NotNull ImmutableSeq<String> argNames;
  private final @NotNull Consumer<PatternSerializer> onStuck;
  private final @NotNull Consumer<PatternSerializer> onMismatch;
  private int bindCount = 0;
  private final boolean inferMeta;

  public PatternSerializer(
    @NotNull SourceBuilder builder,
    @NotNull ImmutableSeq<String> argNames,
    boolean inferMeta,
    @NotNull Consumer<PatternSerializer> onStuck,
    @NotNull Consumer<PatternSerializer> onMismatch
  ) {
    super(builder);
    this.argNames = argNames;
    this.inferMeta = inferMeta;
    this.onStuck = onStuck;
    this.onMismatch = onMismatch;
  }

  /// region Serializing

  private void doSerialize(@NotNull Pat pat, @NotNull String term, @NotNull Once continuation) {
    buildComment(pat.debuggerOnlyToString());

    switch (pat) {
      case Pat.Absurd _ -> buildIfElse("Panic.unreachable()", State.Stuck, continuation);
      case Pat.Bind _ -> {
        onMatchBind(term);
        continuation.run();
      }
      // TODO: match IntegerTerm / ListTerm first
      case Pat.Con con -> multiStage(term, ImmutableSeq.of(
        // mTerm -> solveMeta(con, mTerm),
        mTerm -> buildIfInstanceElse(mTerm, CLASS_CONCALLLIKE, State.Stuck, mmTerm ->
          buildIfElse(STR."\{ExprializeUtils.getCallInstance(mmTerm)} == \{ExprializeUtils.getInstance(NameSerializer.getClassReference(con.ref()))}",
            State.Mismatch, () -> {
              var conArgsTerm = buildLocalVar(TYPE_IMMTERMSEQ,
                nameGen().nextName(), STR."\{mmTerm}.conArgs()");
              doSerialize(con.args().view(), SourceBuilder.fromSeq(conArgsTerm, con.args().size()).view(),
                Once.of(() -> buildUpdate(VARIABLE_SUBSTATE, "true")));
            }))
      ), continuation);
      case Pat.Meta _ -> Panic.unreachable();
      case Pat.ShapedInt shapedInt -> multiStage(term, ImmutableSeq.of(
        // mTerm -> solveMeta(shapedInt, mTerm),
        mTerm -> matchInt(shapedInt, mTerm),
        // do nothing on success, [doSerialize] sets subMatchState, and we will invoke [continuation] when [subMatchState = true]
        mTerm -> doSerialize(shapedInt.constructorForm(), mTerm, Once.of(() -> { }))
      ), continuation);
      case Pat.Tuple tuple -> multiStage(term, ImmutableSeq.of(
        // mTerm -> solveMeta(tuple, mTerm),
        mTerm -> buildIfInstanceElse(mTerm, CLASS_TUPLE, State.Stuck, mmTerm ->
          doSerialize(tuple.elements().view(), SourceBuilder.fromSeq(STR."\{mmTerm}.items()",
            tuple.elements().size()).view(), Once.of(() -> { })))
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
   * Note that {@param preContinuation}s should not invoke {@param continuation}!
   *
   * @param term            the expression be matched, not always a variable reference
   * @param preContinuation matching cases
   * @param continuation    on match success
   */
  private void multiStage(
    @NotNull String term,
    @NotNull ImmutableSeq<Consumer<String>> preContinuation,
    @NotNull Once continuation
  ) {
    var tmpName = nameGen().nextName();
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
          var exprializer = new PatternExprializer(nameGen(), true);
          var result = exprializer.serialize(pat);
          var doSolveMetaResult = STR."\{CLASS_PAT_MATCHER}.doSolveMeta(\{result}, \{stillMetaTerm}.meta())";
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
        // remember to set result

        // Pat.ShapedInt provides no binds
        buildUpdate(VARIABLE_SUBSTATE, "true");
      });
    }, null);
  }

  /**
   * @apiNote {@code pats.sizeEquals(terms)}
   */
  private void doSerialize(@NotNull SeqView<Pat> pats, @NotNull SeqView<String> terms, @NotNull Once continuation) {
    if (pats.isEmpty()) {
      continuation.run();
      return;
    }

    buildComment(pats.map(x -> x.debuggerOnlyToString()).joinToString());
    var pat = pats.getFirst();
    var term = terms.getFirst();
    doSerialize(pat, term, Once.of(() -> doSerialize(pats.drop(1), terms.drop(1), continuation)));
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

  private int bindAmount(@NotNull ImmutableSeq<Pat> pats) {
    var acc = MutableIntValue.create();
    pats.forEach(pat -> pat.consumeBindings((_, _) -> acc.increment()));
    return acc.get();
  }

  /// endregion Java Source Code Generate API

  @Override public PatternSerializer serialize(@NotNull ImmutableSeq<Matching> unit) {
    if (unit.isEmpty()) {
      onMismatch.accept(this);
      return this;
    }
    var bindSize = unit.mapToInt(ImmutableIntSeq.factory(), x -> bindAmount(x.patterns));
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
        Once.of(() -> updateState(jumpCode)));

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
