// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import kala.collection.immutable.ImmutableArray;
import kala.function.CheckedBiFunction;
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
      case JitFn fn -> makeArgs.applyChecked(fn, args -> {
        int shape = fn.metadata().shape();
        if (shape != -1) {
          var operator = AyaShape.ofFn(fn, AyaShape.values()[shape]);
          if (operator != null) return new Jdg.Default(
            new RuleReducer.Fn(operator, 0, ImmutableArray.from(args)), fn.result(args));
        }
        return new Jdg.Default(new FnCall(fn, 0, ImmutableArray.from(args)), fn.result(args));
      });
      case JitData data -> makeArgs.applyChecked(data, args ->
        new Jdg.Default(new DataCall(data, 0, ImmutableArray.from(args)), data.result(args)));
      default -> Panic.unreachable();
    };
  }

  @SuppressWarnings("unchecked")
  static <Ex extends Exception> @NotNull Jdg checkDefApplication(
    @NotNull DefVar<?, ?> defVar,
    @NotNull TyckState state, @NotNull Factory<Ex> makeArgs
  ) throws Ex {
    return switch (defVar.concrete) {
      case FnDecl _ -> {
        var fnVar = (DefVar<FnDef, FnDecl>) defVar;
        var signature = TyckDef.defSignature(fnVar);
        yield makeArgs.applyChecked(signature, args -> {
          var shape = state.shapeFactory().find(new FnDef.Delegate(fnVar));
          var argsSeq = ImmutableArray.from(args);
          var result = signature.result(args);
          if (shape.isDefined()) {
            var operator = AyaShape.ofFn(new FnDef.Delegate(fnVar), shape.get().shape());
            if (operator != null) {
              return new Jdg.Default(new RuleReducer.Fn(operator, 0, argsSeq), result);
            }
          }
          return new Jdg.Default(new FnCall(fnVar, 0, argsSeq), result);
        });
      }
      case DataDecl _ -> {
        var dataVar = (DefVar<DataDef, DataDecl>) defVar;
        var signature = TyckDef.defSignature(dataVar);
        yield makeArgs.applyChecked(signature, args -> new Jdg.Default(
          new DataCall(dataVar, 0, ImmutableArray.from(args)),
          signature.result(args)
        ));
      }
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

          var type = fullSignature.result(realArgs);
          var dataCall = new DataCall(dataVar, 0, ownerArgs);
          var shape = state.shapeFactory().find(new DataDef.Delegate(dataVar))
            .mapNotNull(recog -> AyaShape.ofCon(new ConDef.Delegate(conVar), recog, dataCall))
            .getOrNull();
          if (shape != null) return new Jdg.Default(new RuleReducer.Con(shape, 0, ownerArgs, conArgs), type);
          var wellTyped = new ConCall(conVar, 0, ownerArgs, conArgs);
          return new Jdg.Default(wellTyped, type);
        });
      }
      default -> Panic.unreachable();
    };
  }
}
