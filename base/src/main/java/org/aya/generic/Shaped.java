// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.generic;

import org.aya.core.def.CtorDef;
import org.aya.core.repr.AyaShape;
import org.aya.core.term.CallTerm;
import org.aya.core.term.ErrorTerm;
import org.aya.core.term.Term;
import org.aya.generic.util.InternalException;
import org.aya.tyck.TyckState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Supplier;

public interface Shaped<T> {
  @NotNull AyaShape shape();
  @NotNull Term type();
  @NotNull T constructorForm(@Nullable TyckState state);
  default @NotNull T constructorForm() {
    return constructorForm(null);
  }

  interface Inductively<T> extends Shaped<T> {
    @Override @NotNull Term type();
    @NotNull T makeZero(@NotNull CtorDef zero);
    @NotNull T makeSuc(@NotNull CtorDef suc, @NotNull T t);
    @NotNull T destruct(int repr);
    int repr();

    private <O> boolean sameEncoding(@Nullable TyckState state, @NotNull Shaped<O> other) {
      if (shape() != other.shape()) return false;
      if (!(other instanceof Inductively<?> otherData)) return false;
      var type = type();
      var otherType = otherData.type();
      return switch (type) {
        case CallTerm.Data lhs when otherType instanceof CallTerm.Data rhs ->
          lhs.ref().core == rhs.ref().core;
        case CallTerm.Hole lhs when otherType instanceof CallTerm.Hole rhs -> {
          // same meta always have same solution
          if (lhs.ref() == rhs.ref()) yield true;
          // no state is given, so we can't check the solution
          if (state == null) yield false;
          // different meta can have same solution
          var lSol = findSolution(state, lhs);
          var rSol = findSolution(state, rhs);
          if (lSol == null || rSol == null) yield false;
          yield lSol instanceof CallTerm.Data lData
            && rSol instanceof CallTerm.Data rData
            && lData.ref().core == rData.ref().core;
        }
        default -> false;
      };
    }

    default <O> boolean sameValue(@Nullable TyckState state, @NotNull Shaped<O> other) {
      if (!sameEncoding(state, other)) return false;
      var otherData = ((Inductively<O>) other);
      return repr() == otherData.repr();
    }

    default @Override @NotNull T constructorForm(@Nullable TyckState state) {
      int repr = repr();
      return with(state, (zero, suc) -> {
        if (repr == 0) return makeZero(zero);
        return makeSuc(suc, destruct(repr - 1));
      }, () -> {
        // TODO[literal]: how to handle this?
        throw new InternalException("trying to make constructor form without type solved");
      });
    }

    default <R> R with(
      @NotNull BiFunction<CtorDef, CtorDef, R> block,
      @NotNull Supplier<R> unsolved
    ) {
      return with(null, block, unsolved);
    }

    default <R> R with(
      @Nullable TyckState state,
      @NotNull BiFunction<CtorDef, CtorDef, R> block,
      @NotNull Supplier<R> unsolved
    ) {
      var type = solved(state);
      if (type == null) return unsolved.get();
      var dataDef = type.ref().core;
      var zeroOpt = dataDef.body.find(it -> it.selfTele.sizeEquals(0));
      var sucOpt = dataDef.body.find(it -> it.selfTele.sizeEquals(1));
      if (zeroOpt.isEmpty() || sucOpt.isEmpty()) throw new InternalException("shape recognition bug");
      var zero = zeroOpt.get();
      var suc = sucOpt.get();
      return block.apply(zero, suc);
    }

    private @Nullable CallTerm.Data solved(@Nullable TyckState state) {
      var type = type();
      // already reported as UnsolvedMeta
      if (type instanceof ErrorTerm) return null;
      if (type instanceof CallTerm.Data data) return data;
      if (type instanceof CallTerm.Hole hole) {
        if (state == null) return null;
        var sol = findSolution(state, hole);
        if (sol instanceof CallTerm.Data data) return data;
        // report ill-typed solution? is this possible?
        throw new InternalException("unknown type for literal");
      }
      throw new InternalException("unknown type for literal");
    }

    private @Nullable Term findSolution(@NotNull TyckState state, @NotNull Term maybeHole) {
      if (maybeHole instanceof CallTerm.Hole hole) {
        var sol = state.metas().getOrNull(hole.ref());
        if (sol == null) return null;
        else return findSolution(state, sol);
      }
      return maybeHole;
    }
  }
}
