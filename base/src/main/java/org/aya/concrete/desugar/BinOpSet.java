// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.desugar;

import org.aya.api.error.Reporter;
import org.aya.api.error.SourcePos;
import org.aya.api.util.Assoc;
import org.aya.concrete.Decl;
import org.aya.concrete.Stmt;
import org.aya.concrete.desugar.error.OperatorProblem;
import org.aya.concrete.resolve.context.Context;
import org.glavo.kala.collection.mutable.*;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.tuple.Tuple3;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record BinOpSet(
  @NotNull Reporter reporter,
  @NotNull MutableSet<Elem> ops,
  @NotNull MutableHashMap<Elem, MutableHashSet<Elem>> tighterGraph
) {
  public BinOpSet(@NotNull Reporter reporter) {
    this(reporter, MutableSet.of(), MutableHashMap.of());
  }

  public void bind(@NotNull Tuple2<String, Decl.@NotNull OpDecl> op,
                   @NotNull Stmt.BindPred pred,
                   @NotNull Tuple2<String, Decl.@NotNull OpDecl> target,
                   @NotNull SourcePos sourcePos) {
    var opElem = ensureHasElem(op._1, op._2, sourcePos);
    var targetElem = ensureHasElem(target._1, target._2, sourcePos);
    if (opElem == targetElem) {
      reporter.report(new OperatorProblem.BindSelfError(sourcePos));
      throw new Context.ResolvingInterruptedException();
    }
    if (pred == Stmt.BindPred.Tighter) addTighter(opElem, targetElem);
    else addTighter(targetElem, opElem);
  }

  public PredCmp compare(@NotNull Elem lhs, @NotNull Elem rhs) {
    if (lhs == rhs) return PredCmp.Equal;
    if (hasPath(MutableSet.of(), lhs, rhs)) return PredCmp.Tighter;
    return PredCmp.Undefined;
  }

  private boolean hasPath(@NotNull MutableSet<Elem> book, @NotNull Elem from, @NotNull Elem to) {
    if (book.contains(from)) return false;
    for (var test : tighterGraph.get(from)) {
      if (hasPath(book, test, to)) return true;
    }
    book.add(from);
    return false;
  }

  public Assoc assocOf(@Nullable Tuple3<String, Decl.@NotNull OpDecl, String> opDecl) {
    if (isNotUsedAsOperator(opDecl)) return Assoc.NoFix;
    return ensureHasElem(opDecl._1, opDecl._2).assoc;
  }

  public boolean isNotUsedAsOperator(@Nullable Tuple3<String, Decl.@NotNull OpDecl, String> opDecl) {
    return opDecl == null || opDecl._1.equals(opDecl._3);
  }

  public Elem ensureHasElem(@NotNull String defName, @NotNull Decl.OpDecl opDecl) {
    return ensureHasElem(defName, opDecl, SourcePos.NONE);
  }

  public Elem ensureHasElem(@NotNull String defName, @NotNull Decl.OpDecl opDecl, @NotNull SourcePos sourcePos) {
    var elem = ops.find(e -> e.op == opDecl);
    if (elem.isDefined()) return elem.get();
    var newElem = Elem.from(defName, opDecl, sourcePos);
    ops.add(newElem);
    return newElem;
  }

  private MutableHashSet<Elem> ensureGraphHas(@NotNull Elem elem) {
    return tighterGraph.getOrPut(elem, MutableHashSet::of);
  }

  private void addTighter(@NotNull Elem from, @NotNull Elem to) {
    ensureGraphHas(to);
    ensureGraphHas(from).add(to);
  }

  public void sort() {
    var ind = MutableHashMap.<Elem, Ref<Integer>>of();
    tighterGraph.forEach((from, tos) -> {
      ind.putIfAbsent(from, new Ref<>(0));
      tos.forEach(to -> ind.getOrPut(to, () -> new Ref<>(0)).value += 1);
    });

    var stack = LinkedBuffer.<Elem>of();
    ind.forEach((e, i) -> {
      if (i.value == 0) stack.push(e);
    });

    var count = 0;
    while (stack.isNotEmpty()) {
      var elem = stack.pop();
      count += 1;
      System.out.println(elem.name);
      tighterGraph.get(elem).forEach(to -> {
        if (--ind.get(to).value == 0) stack.push(to);
      });
    }

    if (count != tighterGraph.size()) {
      var circle = Buffer.<Elem>of();
      ind.forEach((e, i) -> {
        if (i.value > 0) circle.append(e);
      });
      reporter.report(new OperatorProblem.CircleError(circle));
      throw new Context.ResolvingInterruptedException();
    }
  }

  public record Elem(
    @NotNull SourcePos firstBind,
    @NotNull Decl.OpDecl op,
    @NotNull String name,
    @NotNull Assoc assoc
  ) {
    private static @NotNull Elem from(@NotNull String defName, Decl.@NotNull OpDecl opDecl, @NotNull SourcePos sourcePos) {
      var opData = opDecl.asOperator();
      if (opData == null) {
        opData = Tuple.of(defName, Assoc.NoFix);
      }
      return new Elem(sourcePos, opDecl, opData._1 != null ? opData._1 : defName, opData._2);
    }
  }

  public enum PredCmp {
    Looser,
    Tighter,
    Undefined,
    Equal,
  }
}
