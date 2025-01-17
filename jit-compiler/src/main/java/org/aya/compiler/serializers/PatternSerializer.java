// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.function.TriConsumer;
import kala.range.primitive.IntRange;
import org.aya.compiler.free.Constants;
import org.aya.compiler.free.FreeCodeBuilder;
import org.aya.compiler.free.FreeJavaExpr;
import org.aya.compiler.free.FreeUtil;
import org.aya.compiler.free.data.LocalVariable;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.TupTerm;
import org.aya.syntax.core.term.call.ConCallLike;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

import java.lang.constant.ConstantDescs;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * We do not serialize meta solve, it is annoying
 */
public final class PatternSerializer {
  @FunctionalInterface
  public interface SuccessContinuation extends TriConsumer<PatternSerializer, FreeCodeBuilder, Integer> {
  }

  // Just for checking
  public final static class Once implements Consumer<FreeCodeBuilder> {
    public static @NotNull Once of(@NotNull Consumer<FreeCodeBuilder> run) { return new Once(run); }
    private final @NotNull Consumer<FreeCodeBuilder> run;
    private boolean dirty = false;

    public Once(@NotNull Consumer<FreeCodeBuilder> run) { this.run = run; }

    @Override
    public void accept(FreeCodeBuilder freeClassBuilder) {
      if (dirty) throw new Panic("Once");
      dirty = true;
      this.run.accept(freeClassBuilder);
    }
  }

  public record Matching(
    int bindCount, @NotNull ImmutableSeq<Pat> patterns,
    @NotNull SuccessContinuation onSucc
  ) { }

  @UnknownNullability LocalVariable result;
  @UnknownNullability LocalVariable matchState;
  @UnknownNullability LocalVariable subMatchState;

  private final @NotNull ImmutableSeq<FreeJavaExpr> argNames;
  private final @NotNull Consumer<FreeCodeBuilder> onFailed;
  private final boolean isOverlap;
  private int bindCount = 0;

  public PatternSerializer(
    @NotNull ImmutableSeq<FreeJavaExpr> argNames,
    @NotNull Consumer<FreeCodeBuilder> onFailed,
    boolean isOverlap
  ) {
    this.argNames = argNames;
    this.onFailed = onFailed;
    this.isOverlap = isOverlap;
  }

  // region Serializing

  private void doSerialize(
    @NotNull FreeCodeBuilder builder,
    @NotNull Pat pat,
    @NotNull FreeJavaExpr term,
    @NotNull Once onMatchSucc
  ) {
    switch (pat) {
      case Pat.Misc misc -> {
        switch (misc) {
          case Absurd -> builder.unreachable();
          case UntypedBind -> {
            onMatchBind(builder, term);
            onMatchSucc.accept(builder);
          }
        }
      }
      case Pat.Bind _ -> {
        onMatchBind(builder, term);
        onMatchSucc.accept(builder);
      }

      case Pat.Con con -> builder.ifInstanceOf(term, FreeUtil.fromClass(ConCallLike.class),
        (builder1, conTerm) -> builder1.ifRefEqual(
          AbstractExprializer.getRef(builder1, CallKind.Con, conTerm.ref()),
          AbstractExprializer.getInstance(builder1, con.ref()),
          builder2 -> {
            var conArgsTerm = builder2.invoke(Constants.CONARGS, conTerm.ref(), ImmutableSeq.empty());
            var conArgs = AbstractExprializer.fromSeq(
              builder2,
              Constants.CD_Term,
              conArgsTerm,
              con.args().size()
            );

            doSerialize(builder2, con.args().view(), conArgs.view(), onMatchSucc);
          }, null /* mismatch, do nothing */
        ), this::onStuck);
      case Pat.Meta _ -> Panic.unreachable();
      case Pat.ShapedInt shapedInt -> multiStage(builder, term, ImmutableSeq.of(
        // mTerm -> solveMeta(shapedInt, mTerm),
        (builder0, mTerm) ->
          matchInt(builder0, shapedInt, mTerm),
        (builder0, mTerm) ->
          doSerialize(builder0, shapedInt.constructorForm(), builder.refVar(mTerm),
            // There will a sequence of [subMatchState = true] if there are a lot of [Pat.ShapedInt],
            // but our optimizer will fix them
            Once.of(builder1 -> updateSubstate(builder1, true)))
      ), onMatchSucc);
      case Pat.Tuple(var l, var r) ->
        builder.ifInstanceOf(term, FreeUtil.fromClass(TupTerm.class), (builder0, tupTerm) -> {
          var lhs = builder0.invoke(Constants.TUP_LHS, tupTerm.ref(), ImmutableSeq.empty());
          doSerialize(builder0, l, lhs, Once.of(builder1 -> {
            var rhs = builder0.invoke(Constants.TUP_RHS, tupTerm.ref(), ImmutableSeq.empty());
            doSerialize(builder1, r, rhs, onMatchSucc);
          }));
        }, this::onStuck);
    }
  }

  /**
   * Generate multi case matching, these local variable are available:
   * <p>
   * Note that {@param preContinuation}s should not invoke {@param continuation}!
   *
   * @param term            the expression be matched, not always a variable reference
   * @param preContinuation matching cases, only the last one can invoke multiStage
   * @param continuation    on match success
   */
  private void multiStage(
    @NotNull FreeCodeBuilder builder,
    @NotNull FreeJavaExpr term,
    @NotNull ImmutableSeq<BiConsumer<FreeCodeBuilder, LocalVariable>> preContinuation,
    @NotNull Once continuation
  ) {
    updateSubstate(builder, false);
    var tmpName = builder.makeVar(Term.class, term);

    for (var pre : preContinuation) {
      builder.ifNotTrue(subMatchState, builder0 ->
        pre.accept(builder0, tmpName), null);
    }

    builder.ifTrue(subMatchState, continuation, null);
  }

  private void matchInt(@NotNull FreeCodeBuilder builder, @NotNull Pat.ShapedInt pat, @NotNull LocalVariable term) {
    builder.ifInstanceOf(builder.refVar(term), FreeUtil.fromClass(IntegerTerm.class), (builder0, intTerm) -> {
      var intTermRepr = builder0.invoke(
        Constants.INT_REPR,
        builder0.refVar(intTerm),
        ImmutableSeq.empty()
      );

      builder0.ifIntEqual(intTermRepr, pat.repr(), builder1 -> {
        // Pat.ShapedInt provides no binds
        updateSubstate(builder1, true);
      }, null);
    }, null);
  }

  /**
   * @apiNote {@code pats.sizeEquals(terms)}
   */
  private void doSerialize(
    @NotNull FreeCodeBuilder builder,
    @NotNull SeqView<Pat> pats,
    @NotNull SeqView<FreeJavaExpr> terms,
    @NotNull Once continuation
  ) {
    if (pats.isEmpty()) {
      continuation.accept(builder);
      return;
    }

    var pat = pats.getFirst();
    var term = terms.getFirst();
    doSerialize(builder, pat, term,
      Once.of(builder0 -> doSerialize(builder0, pats.drop(1), terms.drop(1), continuation)));
  }
  // endregion Serializing

  // region Java Source Code Generate API
  private void onStuck(@NotNull FreeCodeBuilder builder) {
    if (!isOverlap) builder.breakOut();
  }

  private void updateSubstate(@NotNull FreeCodeBuilder builder, boolean state) {
    builder.updateVar(subMatchState, builder.iconst(state));
  }

  private void updateState(@NotNull FreeCodeBuilder builder, int state) {
    builder.updateVar(matchState, builder.iconst(state));
  }

  private void onMatchBind(@NotNull FreeCodeBuilder builder, @NotNull FreeJavaExpr term) {
    builder.exec(
      builder.invoke(Constants.MUTSEQ_SET, result.ref(), ImmutableSeq.of(
        builder.iconst(bindCount++),
        term
      ))
    );
  }
  // endregion Java Source Code Generate API

  public PatternSerializer serialize(@NotNull FreeCodeBuilder builder, @NotNull ImmutableSeq<Matching> unit) {
    if (unit.isEmpty()) {
      onFailed.accept(builder);
      return this;
    }

    var bindSize = unit.mapToInt(ImmutableIntSeq.factory(), Matching::bindCount);
    int maxBindSize = bindSize.max();

    // var result = MutableSeq.fill(maxBindCount, null);
    result = builder.makeVar(Constants.CD_MutableSeq,
      builder.invoke(Constants.MUTSEQ, ImmutableSeq.of(
        builder.iconst(maxBindSize),
        builder.aconstNull(Constants.CD_Term)
      ))
    );
    // whether the match success or mismatch, 0 implies mismatch
    matchState = builder.makeVar(ConstantDescs.CD_int, builder.iconst(0));
    subMatchState = builder.makeVar(ConstantDescs.CD_boolean, builder.iconst(false));

    builder.breakable(mBuilder -> unit.forEachIndexed((idx, clause) -> {
      var jumpCode = idx + 1;
      bindCount = 0;
      doSerialize(
        mBuilder,
        clause.patterns.view(),
        argNames.view(),
        Once.of(builder1 -> {
          updateState(builder1, jumpCode);
          builder1.breakOut();
        })
      );
    }));

    // 0 ..= unit.size()
    var range = IntRange.closed(0, unit.size()).collect(ImmutableIntSeq.factory());
    builder.switchCase(matchState, range, (mBuilder, i) -> {
      if (i == 0) {
        onFailed.accept(mBuilder);
        return;
      }

      assert i > 0;
      var realIdx = i - 1;
      unit.get(realIdx).onSucc.accept(this, mBuilder, bindSize.get(realIdx));
    }, FreeCodeBuilder::unreachable);

    return this;
  }
}
