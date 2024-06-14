// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import kala.collection.Seq;
import kala.collection.SeqView;
import kala.collection.immutable.ImmutableArray;
import kala.function.CheckedBiFunction;
import kala.function.CheckedSupplier;
import org.aya.generic.stmt.Shaped;
import org.aya.syntax.compile.JitCon;
import org.aya.syntax.compile.JitData;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.compile.JitPrim;
import org.aya.syntax.concrete.stmt.decl.*;
import org.aya.syntax.core.def.*;
import org.aya.syntax.core.repr.AyaShape;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.*;
import org.aya.syntax.ref.DefVar;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.telescope.AbstractTele;
import org.aya.tyck.Jdg;
import org.aya.tyck.TyckState;
import org.aya.util.error.Panic;
import org.aya.util.error.SourcePos;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public interface AppTycker {
  /**
   * <pre>
   * Signature (0th param) --------> Argument Parser (this interface)
   *                                        |
   *                                  [ arguments ]
   *                                        |
   *                                        v
   * Well-typed Call (result) <---- Factory (1st param)
   * </pre>
   */
  @FunctionalInterface
  interface Factory<Ex extends Exception> extends
    CheckedBiFunction<AbstractTele, Function<Term[], Jdg>, Jdg, Ex> {
  }

  interface ClassCallScope<Ex extends Exception> {
    <R> R subscopedClass(@NotNull CheckedSupplier<R, Ex> block) throws Ex;
    LocalVar thisClass();
  }

  record CheckAppData<Ex extends Exception>(
    @NotNull TyckState state, @NotNull ClassCallScope<Ex> scoper, @NotNull SourcePos pos,
    int argsCount, int lift, @NotNull Factory<Ex> makeArgs
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
      case ClassDecl _ -> checkClassCall(input.makeArgs, input.scoper, input.pos, input.argsCount, input.lift,
        new ClassDef.Delegate((DefVar<ClassDef, ClassDecl>) defVar));
      case ClassMember _ -> checkProjCall(input.makeArgs, input.lift,
        new MemberDef.Delegate((DefVar<MemberDef, ClassMember>) defVar));
      case Decl any -> throw new Panic(any.getClass().getCanonicalName());
    };
  }

  private static <Ex extends Exception> Jdg
  checkConCall(@NotNull TyckState state, @NotNull Factory<Ex> makeArgs, int lift, ConDefLike conVar) throws Ex {
    var dataVar = conVar.dataRef();

    // ownerTele + selfTele
    var fullSignature = conVar.signature().lift(lift);

    return makeArgs.applyChecked(fullSignature, args -> {
      var realArgs = ImmutableArray.from(args);
      var ownerArgs = realArgs.take(conVar.ownerTeleSize());
      var conArgs = realArgs.drop(conVar.ownerTeleSize());

      var type = (DataCall) fullSignature.result(realArgs);
      var shape = state.shapeFactory().find(dataVar)
        .mapNotNull(recog -> AyaShape.ofCon(conVar, recog, type))
        .getOrNull();
      if (shape != null) return new Jdg.Default(new RuleReducer.Con(shape, 0, ownerArgs, conArgs), type);
      var wellTyped = new ConCall(conVar, ownerArgs, 0, conArgs);
      return new Jdg.Default(wellTyped, type);
    });
  }
  private static <Ex extends Exception> Jdg
  checkPrimCall(@NotNull TyckState state, @NotNull Factory<Ex> makeArgs, int lift, PrimDefLike primVar) throws Ex {
    var signature = primVar.signature().lift(lift);
    return makeArgs.applyChecked(signature, args -> new Jdg.Default(
      state.primFactory().unfold(new PrimCall(primVar, 0, ImmutableArray.from(args)), state),
      signature.result(args)
    ));
  }
  private static <Ex extends Exception> Jdg
  checkDataCall(@NotNull Factory<Ex> makeArgs, int lift, DataDefLike data) throws Ex {
    var signature = data.signature().lift(lift);
    return makeArgs.applyChecked(signature, args -> new Jdg.Default(
      new DataCall(data, 0, ImmutableArray.from(args)),
      signature.result(args)
    ));
  }
  private static <Ex extends Exception> @NotNull Jdg checkFnCall(
    @NotNull Factory<Ex> makeArgs, int lift, FnDefLike fnDef,
    Shaped.Applicable<FnDefLike> operator
  ) throws Ex {
    var signature = fnDef.signature().lift(lift);
    return makeArgs.applyChecked(signature, args -> {
      var argsSeq = ImmutableArray.from(args);
      var result = signature.result(args);
      if (operator != null) {
        return new Jdg.Default(new RuleReducer.Fn(operator, 0, argsSeq), result);
      }
      var fnCall = new FnCall(fnDef, 0, argsSeq);
      return new Jdg.Default(fnCall, result);
    });
  }

  private static <Ex extends Exception> Jdg checkClassCall(
    @NotNull Factory<Ex> makeArgs, @NotNull ClassCallScope<Ex> scoper, @NotNull SourcePos pos,
    int argsCount, int lift, @NotNull ClassDefLike clazz
  ) throws Ex {
    var appliedParams = ofClassMembers(clazz, argsCount).lift(lift);
    // TODO: We may just accept a LocalVar and do subscopedClass in ExprTycker as long as appliedParams won't be affected.
    return scoper.subscopedClass(() -> makeArgs.applyChecked(appliedParams, args -> {
      var self = scoper.thisClass();
      return new Jdg.Default(
        new ClassCall(self, clazz, 0, ImmutableArray.from(args)),
        appliedParams.result(args)
      );
    }));
  }

  static @NotNull <Ex extends Exception> Jdg checkProjCall(
    @NotNull Factory<Ex> makeArgs, int lift,
    @NotNull MemberDefLike member
  ) throws Ex {
    var signature = member.signature().lift(lift);
    return makeArgs.applyChecked(signature, args -> {
      assert args.length >= 1;
      var fieldArgs = ImmutableArray.fill(args.length - 1, i -> args[i + 1]);
      return new Jdg.Default(
        new FieldCall(args[0], member, 0, fieldArgs),
        signature.result(args)
      );
    });
  }

  static @NotNull AbstractTele ofClassMembers(@NotNull ClassDefLike def, int memberCount) {
    return switch (def) {
      case ClassDef.Delegate delegate -> new TakeMembers(delegate.core(), memberCount);
    };
  }

  record TakeMembers(@NotNull ClassDef clazz, @Override int telescopeSize) implements AbstractTele {
    @Override public boolean telescopeLicit(int i) { return true; }
    @Override public @NotNull String telescopeName(int i) {
      assert i < telescopeSize;
      return clazz.members().get(i).ref().name();
    }

    // class Foo
    // | foo : A
    // | + : A -> A -> A
    // | bar : Fn (x : Foo A) -> (x.foo) self.+ (self.foo)
    //                  instantiate these!   ^       ^
    @Override public @NotNull Term telescope(int i, Seq<Term> teleArgs) {
      // teleArgs are former members
      assert i < telescopeSize;
      var member = clazz.members().get(i);
      // TODO: instantiate self projection with teleArgs
      return TyckDef.defSignature(member.ref()).makePi();
    }
    @Override public @NotNull Term result(Seq<Term> teleArgs) {
      // Use SigmaTerm::lub
      throw new UnsupportedOperationException("TODO");
    }
    @Override public @NotNull SeqView<String> namesView() {
      return clazz.members().sliceView(0, telescopeSize).map(i -> i.ref().name());
    }
  }
}
