// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import kala.collection.immutable.ImmutableArray;
import kala.function.CheckedBiFunction;
import org.aya.generic.stmt.Shaped;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.compile.JitPrim;
import org.aya.syntax.concrete.stmt.decl.DataCon;
import org.aya.syntax.concrete.stmt.decl.DataDecl;
import org.aya.syntax.concrete.stmt.decl.FnDecl;
import org.aya.syntax.concrete.stmt.decl.PrimDecl;
import org.aya.syntax.core.def.*;
import org.aya.syntax.core.repr.AyaShape;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.*;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.telescope.AbstractTele;
import org.aya.tyck.Jdg;
import org.aya.tyck.TyckState;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface AppTycker {
  @FunctionalInterface
  interface Factory<Ex extends Exception> extends
    CheckedBiFunction<AbstractTele, Function<Term[], Jdg>, Jdg, Ex> {
  }
  record CheckAppData<Ex extends Exception>(
    @NotNull TyckState state, int lift, @NotNull Factory<Ex> makeArgs
  ) { }

  static <Ex extends Exception> @NotNull Jdg
  checkCompiledApplication(@NotNull AbstractTele def, CheckAppData<Ex> input) throws Ex {
    return switch (def) {
      case JitFn fn -> {
        int shape = fn.metadata().shape();
        var operator = shape != -1 ? AyaShape.ofFn(fn, AyaShape.values()[shape]) : null;
        yield checkFnCall(input.makeArgs, input.lift, fn, operator);
      }
      case JitData data -> checkDataCall(input.makeArgs, input.lift, data);
      case JitPrim prim -> checkPrimCall(input.state, input.makeArgs, input.lift, prim);
      case JitCon con -> checkConCall(input.state, input.makeArgs, input.lift, con);
      default -> throw new Panic(def.getClass().getCanonicalName());
    };
  }

  @SuppressWarnings("unchecked")
  static <Ex extends Exception> @NotNull Jdg checkDefApplication(
    @NotNull DefVar<?, ?> defVar, @NotNull CheckAppData<Ex> input
  ) throws Ex {
    return switch (defVar.concrete) {
      case FnDecl _ -> {
        var fnDef = new FnDef.Delegate((DefVar<FnDef, FnDecl>) defVar);
        var op = input.state.shapeFactory().find(fnDef).map(recog -> AyaShape.ofFn(fnDef, recog.shape())).getOrNull();
        yield checkFnCall(input.makeArgs, input.lift, fnDef, op);
      }
      // Extracted to prevent pervasive influence of suppression of unchecked warning.
      case DataDecl _ -> checkDataCall(input.makeArgs, input.lift,
        new DataDef.Delegate((DefVar<DataDef, DataDecl>) defVar));
      case PrimDecl _ -> checkPrimCall(input.state, input.makeArgs, input.lift,
        new PrimDef.Delegate((DefVar<PrimDef, PrimDecl>) defVar));
      case DataCon _ -> checkConCall(input.state, input.makeArgs, input.lift,
        new ConDef.Delegate((DefVar<ConDef, DataCon>) defVar));
      default -> Panic.unreachable();
    };
  }

  private static <Ex extends Exception> Jdg
  checkConCall(@NotNull TyckState state, @NotNull Factory<Ex> makeArgs, int lift, ConDefLike conVar) throws Ex {
    var dataVar = conVar.dataRef();

    // ownerTele + selfTele
    var fullSignature = conVar.signature();

    return makeArgs.applyChecked(fullSignature, args -> {
      var realArgs = ImmutableArray.from(args);
      var ownerArgs = realArgs.take(conVar.ownerTeleSize());
      var conArgs = realArgs.drop(conVar.ownerTeleSize());

      var type = (DataCall) fullSignature.result(realArgs);
      var shape = state.shapeFactory().find(dataVar)
        .mapNotNull(recog -> AyaShape.ofCon(conVar, recog, type))
        .getOrNull();
      if (shape != null) return new Jdg.Default(new RuleReducer.Con(shape, lift, ownerArgs, conArgs), type);
      var wellTyped = new ConCall(conVar, ownerArgs, lift, conArgs);
      return new Jdg.Default(wellTyped, type);
    });
  }
  private static <Ex extends Exception> Jdg
  checkPrimCall(@NotNull TyckState state, @NotNull Factory<Ex> makeArgs, int lift, PrimDefLike primVar) throws Ex {
    var signature = primVar.signature();
    return makeArgs.applyChecked(signature, args -> new Jdg.Default(
      state.primFactory().unfold(new PrimCall(primVar, lift, ImmutableArray.from(args)), state),
      signature.result(args)
    ));
  }
  private static <Ex extends Exception> Jdg
  checkDataCall(@NotNull Factory<Ex> makeArgs, int lift, DataDefLike data) throws Ex {
    var signature = new AbstractTele.Lift(data.signature(), lift);
    return makeArgs.applyChecked(signature, args -> new Jdg.Default(
      new DataCall(data, lift, ImmutableArray.from(args)),
      signature.result(args)
    ));
  }
  private static <Ex extends Exception> @NotNull Jdg checkFnCall(
    @NotNull Factory<Ex> makeArgs, int lift, FnDefLike fnDef,
    Shaped.Applicable<FnDefLike> operator
  ) throws Ex {
    var signature = fnDef.signature();
    return makeArgs.applyChecked(signature, args -> {
      var argsSeq = ImmutableArray.from(args);
      var result = signature.result(args);
      if (operator != null) {
        return new Jdg.Default(new RuleReducer.Fn(operator, lift, argsSeq), result);
      }
      return new Jdg.Default(new FnCall(fnDef, lift, argsSeq), result);
    });
  }
}
