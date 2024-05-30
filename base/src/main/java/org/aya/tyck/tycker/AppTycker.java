// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import kala.collection.immutable.ImmutableArray;
import kala.function.CheckedBiFunction;
import org.aya.generic.stmt.Shaped;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.compile.JitTele;
import org.aya.syntax.concrete.stmt.decl.DataCon;
import org.aya.syntax.concrete.stmt.decl.DataDecl;
import org.aya.syntax.concrete.stmt.decl.FnDecl;
import org.aya.syntax.concrete.stmt.decl.PrimDecl;
import org.aya.syntax.core.def.*;
import org.aya.syntax.core.repr.AyaShape;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.*;
import org.aya.syntax.ref.DefVar;
import org.aya.tyck.Jdg;
import org.aya.tyck.TyckState;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface AppTycker {
  @FunctionalInterface
  interface Factory<Ex extends Exception> extends
    CheckedBiFunction<JitTele, Function<Term[], Jdg>, Jdg, Ex> {
  }

  static <Ex extends Exception> @NotNull Jdg checkCompiledApplication(
    JitTele def, @NotNull Factory<Ex> makeArgs
  ) throws Ex {
    return switch (def) {
      case JitFn fn -> {
        int shape = fn.metadata().shape();
        var operator = shape != -1 ? AyaShape.ofFn(fn, AyaShape.values()[shape]) : null;
        yield checkFnCall(makeArgs, fn, operator);
      }
      case JitData data -> checkDataCall(makeArgs, data);
      default -> throw new Panic(def.getClass().getCanonicalName());
    };
  }

  @SuppressWarnings("unchecked")
  static <Ex extends Exception> @NotNull Jdg checkDefApplication(
    @NotNull DefVar<?, ?> defVar,
    @NotNull TyckState state, @NotNull Factory<Ex> makeArgs
  ) throws Ex {
    return switch (defVar.concrete) {
      case FnDecl _ -> {
        var fnDef = new FnDef.Delegate((DefVar<FnDef, FnDecl>) defVar);
        var op = state.shapeFactory().find(fnDef).map(recog -> AyaShape.ofFn(fnDef, recog.shape())).getOrNull();
        yield checkFnCall(makeArgs, fnDef, op);
      }
      // Extracted to prevent pervasive influence of suppression of unchecked warning.
      case DataDecl _ -> checkDataCall(makeArgs, new DataDef.Delegate((DefVar<DataDef, DataDecl>) defVar));
      case PrimDecl _ -> {
        var primVar = (DefVar<PrimDef, PrimDecl>) defVar;
        var signature = TyckDef.defSignature(primVar);
        yield makeArgs.applyChecked(signature, args -> new Jdg.Default(
          state.primFactory().unfold(new PrimCall(primVar, 0, ImmutableArray.from(args)), state),
          signature.result(args)
        ));
      }
      case DataCon _ -> {
        var conVar = (DefVar<ConDef, DataCon>) defVar;
        var conCore = conVar.core;
        assert conCore != null;
        var dataVar = conCore.dataRef;

        // ownerTele + selfTele
        var fullSignature = TyckDef.defSignature(conVar);
        var ownerTele = conCore.ownerTele;

        yield makeArgs.applyChecked(fullSignature, args -> {
          var realArgs = ImmutableArray.from(args);
          var ownerArgs = realArgs.take(ownerTele.size());
          var conArgs = realArgs.drop(ownerTele.size());

          var type = (DataCall) fullSignature.result(realArgs);
          var shape = state.shapeFactory().find(new DataDef.Delegate(dataVar))
            .mapNotNull(recog -> AyaShape.ofCon(new ConDef.Delegate(conVar), recog, type))
            .getOrNull();
          if (shape != null) return new Jdg.Default(new RuleReducer.Con(shape, 0, ownerArgs, conArgs), type);
          var wellTyped = new ConCall(conVar, 0, ownerArgs, conArgs);
          return new Jdg.Default(wellTyped, type);
        });
      }
      default -> Panic.unreachable();
    };
  }
  private static <Ex extends Exception> Jdg
  checkDataCall(@NotNull Factory<Ex> makeArgs, DataDefLike data) throws Ex {
    var signature = data.signature();
    return makeArgs.applyChecked(signature, args -> new Jdg.Default(
      new DataCall(data, 0, ImmutableArray.from(args)),
      signature.result(args)
    ));
  }
  private static <Ex extends Exception> Jdg
  checkFnCall(@NotNull Factory<Ex> makeArgs, FnDefLike fnDef, Shaped.Applicable<FnDefLike> operator) throws Ex {
    var signature = fnDef.signature();
    return makeArgs.applyChecked(signature, args -> {
      var argsSeq = ImmutableArray.from(args);
      var result = signature.result(args);
      if (operator != null) {
        return new Jdg.Default(new RuleReducer.Fn(operator, 0, argsSeq), result);
      }
      return new Jdg.Default(new FnCall(fnDef, 0, argsSeq), result);
    });
  }
}
