// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableIntSeq;
import kala.function.TriConsumer;
import kala.range.primitive.IntRange;
import org.aya.compiler.morphism.AstUtil;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.ast.AstCodeBuilder;
import org.aya.compiler.morphism.ast.AstExpr;
import org.aya.compiler.morphism.ast.AstVariable;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.TupTerm;
import org.aya.syntax.core.term.call.ConCallLike;
import org.aya.syntax.core.term.repr.IntegerTerm;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * We do not serialize meta solve, it is annoying
 */
public final class PatternSerializer {
  @FunctionalInterface
  public interface SuccessContinuation extends TriConsumer<PatternSerializer, AstCodeBuilder, Integer> {
  }

  // Just for checking
  public final static class Once implements Consumer<AstCodeBuilder> {
    public static @NotNull Once of(@NotNull Consumer<AstCodeBuilder> run) { return new Once(run); }
    private final @NotNull Consumer<AstCodeBuilder> run;
    private boolean dirty = false;

    public Once(@NotNull Consumer<AstCodeBuilder> run) { this.run = run; }

    @Override
    public void accept(AstCodeBuilder freeClassBuilder) {
      if (dirty) throw new Panic("Once");
      dirty = true;
      this.run.accept(freeClassBuilder);
    }
  }

  public record Matching(
    int bindCount, @NotNull ImmutableSeq<Pat> patterns,
    @NotNull SuccessContinuation onSucc
  ) { }

  @UnknownNullability ImmutableSeq<AstVariable> result;
  @UnknownNullability AstVariable matchState;
  @UnknownNullability AstVariable subMatchState;

  private final @NotNull ImmutableSeq<AstVariable> argNames;
  private final @NotNull Consumer<AstCodeBuilder> onFailed;
  private final @NotNull SerializerContext context;
  private final boolean isOverlap;
  private int bindCount = 0;

  public PatternSerializer(
    @NotNull ImmutableSeq<AstVariable> argNames,
    @NotNull Consumer<AstCodeBuilder> onFailed,
    @NotNull SerializerContext context,
    boolean isOverlap
  ) {
    this.argNames = argNames;
    this.onFailed = onFailed;
    this.context = context;
    this.isOverlap = isOverlap;
  }

  // region Serializing

  private void doSerialize(
    @NotNull AstCodeBuilder builder,
    @NotNull Pat pat,
    @NotNull AstVariable term,
    @NotNull Once onMatchSucc
  ) {
    switch (pat) {
      case Pat.Misc misc -> {
        switch (misc) {
          case Absurd -> builder.unreachable();
          // case UntypedBind -> {
          //   onMatchBind(builder, term);
          //   onMatchSucc.accept(builder);
          // }
        }
      }
      case Pat.Bind _ -> {
        builder.updateVar(result.get(bindCount++), new AstExpr.Ref(term));
        onMatchSucc.accept(builder);
      }

      case Pat.Con con -> {
        var whnf = context.whnf(builder, term);
        builder.ifInstanceOf(whnf, AstUtil.fromClass(ConCallLike.class),
          (builder1, conTerm) -> builder1.ifRefEqual(
            AbstractExprializer.getRef(builder1, CallKind.Con, conTerm),
            AbstractExprializer.getInstance(builder1, con.ref()),
            builder2 -> {
              var conArgsTerm = builder2.bindExpr(new AstExpr.Invoke(Constants.CONARGS, conTerm, ImmutableSeq.empty()));
              var conArgs = AbstractExprializer.fromSeq(
                builder2,
                Constants.CD_Term,
                conArgsTerm,
                con.args().size()
              );

              doSerialize(builder2, con.args().view(), conArgs.view(), onMatchSucc);
            }, null /* mismatch, do nothing */
          ), this::onStuck);
      }
      case Pat.Meta _ -> Panic.unreachable();
      case Pat.ShapedInt shapedInt -> {
        var whnf = context.whnf(builder, term);
        multiStage(builder, whnf, ImmutableSeq.of(
          // mTerm -> solveMeta(shapedInt, mTerm),
          (builder0, mTerm) ->
            matchInt(builder0, shapedInt, mTerm),
          (builder0, mTerm) ->
            doSerialize(builder0, shapedInt.constructorForm(), mTerm,
              // There will a sequence of [subMatchState = true] if there are a lot of [Pat.ShapedInt],
              // but our optimizer will fix them
              Once.of(builder1 -> updateSubstate(builder1, true)))
        ), onMatchSucc);
      }
      case Pat.Tuple(var l, var r) -> {
        var whnf = context.whnf(builder, term);
        builder.ifInstanceOf(whnf, AstUtil.fromClass(TupTerm.class), (builder0, tupTerm) -> {
          // TODO: use doSerialize on many pat version
          var lhs = new AstExpr.Invoke(Constants.TUP_LHS, tupTerm, ImmutableSeq.empty());
          doSerialize(builder0, l, builder0.bindExpr(lhs), Once.of(builder1 -> {
            var rhs = new AstExpr.Invoke(Constants.TUP_RHS, tupTerm, ImmutableSeq.empty());
            doSerialize(builder1, r, builder1.bindExpr(rhs), onMatchSucc);
          }));
        }, this::onStuck);
      }
    }
  }

  /**
   * Generate multi case matching, these local variable are available:
   * <p>
   * Note that {@param preContinuation}s should not invoke {@param continuation}!
   *
   * @param term            an immutable variable that be matched
   * @param preContinuation matching cases, only the last one can invoke multiStage, must not modify the given variable
   * @param continuation    on match success
   */
  private void multiStage(
    @NotNull AstCodeBuilder builder,
    @NotNull AstVariable term,
    @NotNull ImmutableSeq<BiConsumer<AstCodeBuilder, AstVariable>> preContinuation,
    @NotNull Once continuation
  ) {
    updateSubstate(builder, false);

    // all [preContinuation] won't modify or store [term], and [term] will not be updated
    // so we pass [term] directly is safe

    for (var pre : preContinuation) {
      builder.ifNotTrue(subMatchState, builder0 ->
        pre.accept(builder0, term), null);
    }

    builder.ifTrue(subMatchState, continuation, null);
  }

  private void matchInt(@NotNull AstCodeBuilder builder, @NotNull Pat.ShapedInt pat, @NotNull AstVariable term) {
    builder.ifInstanceOf(term, AstUtil.fromClass(IntegerTerm.class), (builder0, intTerm) -> {
      var intTermRepr = new AstExpr.Invoke(
        Constants.INT_REPR,
        intTerm,
        ImmutableSeq.empty()
      );

      builder0.ifIntEqual(builder0.bindExpr(intTermRepr), pat.repr(), builder1 -> {
        // Pat.ShapedInt provides no binds
        updateSubstate(builder1, true);
      }, null);
    }, null);
  }

  /**
   * @apiNote {@code pats.sizeEquals(terms)}
   */
  private void doSerialize(
    @NotNull AstCodeBuilder builder,
    @NotNull SeqView<Pat> pats,
    @NotNull SeqView<AstVariable> terms,
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
  private void onStuck(@NotNull AstCodeBuilder builder) {
    if (!isOverlap) builder.breakOut();
  }

  private void updateSubstate(@NotNull AstCodeBuilder builder, boolean state) {
    builder.updateVar(subMatchState, new AstExpr.Bconst(state));
  }

  private void updateState(@NotNull AstCodeBuilder builder, int state) {
    builder.updateVar(matchState, new AstExpr.Iconst(state));
  }
  // endregion Java Source Code Generate API

  public PatternSerializer serialize(@NotNull AstCodeBuilder builder, @NotNull ImmutableSeq<Matching> unit) {
    if (unit.isEmpty()) {
      onFailed.accept(builder);
      return this;
    }

    var bindSize = unit.mapToInt(ImmutableIntSeq.factory(), Matching::bindCount);
    int binds = bindSize.max();

    // generates local term variables
    result = ImmutableSeq.fill(binds, _ -> builder.bindExpr(new AstExpr.Null(Constants.CD_Term)));

    // whether the match success or mismatch, 0 implies mismatch
    matchState = builder.iconst(0);
    subMatchState = builder.iconst(false);

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
    }, AstCodeBuilder::unreachable);

    return this;
  }
}
