// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.priority;

import org.aya.api.util.Assoc;
import org.aya.concrete.Decl;
import org.aya.concrete.Stmt;
import org.glavo.kala.collection.mutable.MutableHashSet;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.tuple.Tuple3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record BinOpSet(@NotNull MutableHashSet<Elem> ops) {
  public BinOpSet() {
    this(MutableHashSet.of());
  }

  public void bind(@NotNull Tuple2<String, Decl.@NotNull OpDecl> op,
                   @NotNull Stmt.BindPred pred,
                   @NotNull Tuple2<String, Decl.@NotNull OpDecl> target) {
    var opElem = ensureHasElem(op._1, op._2);
    var targetElem = ensureHasElem(target._1, target._2);
    opElem.register(pred, targetElem);
    targetElem.register(pred.invert(), opElem);
  }

  public Assoc assocOf(@Nullable Tuple3<String, Decl.@NotNull OpDecl, String> opDecl) {
    if (isNotUsedAsOperator(opDecl)) return Assoc.NoFix;
    return ensureHasElem(opDecl._1, opDecl._2).assoc;
  }

  public boolean isNotUsedAsOperator(@Nullable Tuple3<String, Decl.@NotNull OpDecl, String> opDecl) {
    return opDecl == null || opDecl._1.equals(opDecl._3);
  }

  public Elem ensureHasElem(@NotNull String defName, @NotNull Decl.OpDecl opDecl) {
    var elem = ops.find(e -> e.op == opDecl);
    if (elem.isDefined()) return elem.get();
    var opData = opDecl.asOperator();
    if (opData == null) {
      opData = Tuple.of(defName, Assoc.NoFix);
    }
    var newElem = new Elem(opDecl, opData._1, opData._2,
      MutableHashSet.of(), MutableHashSet.of());
    ops.add(newElem);
    return newElem;
  }

  public record Elem(
    @NotNull Decl.OpDecl op,
    @Nullable String name,
    @NotNull Assoc assoc,
    @NotNull MutableHashSet<Elem> tighter,
    @NotNull MutableHashSet<Elem> looser
  ) {
    void register(@NotNull Stmt.BindPred pred, @NotNull Elem that) {
      if (pred == Stmt.BindPred.Looser) thisIsLooserThan(that);
      else thisIsTighterThan(that);
    }

    public PredCmp compareWith(@NotNull Elem that) {
      if (tighter.contains(that)) return PredCmp.Tighter;
      if (looser.contains(that)) return PredCmp.Looser;
      else return PredCmp.Undefined;
    }

    private void thisIsLooserThan(@NotNull Elem that) {
      // TODO[kiva]: report
      if (compareWith(that) == PredCmp.Tighter) throw new IllegalStateException();
      looser.add(that);
    }

    private void thisIsTighterThan(@NotNull Elem that) {
      // TODO[kiva]: report
      if (compareWith(that) == PredCmp.Looser) throw new IllegalStateException();
      tighter.add(that);
    }
  }

  public enum PredCmp {
    Looser,
    Tighter,
    Undefined,
  }
}
