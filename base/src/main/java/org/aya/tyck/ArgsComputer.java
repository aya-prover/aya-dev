// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

import kala.collection.immutable.ImmutableArray;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.Instance;
import org.aya.generic.term.DTKind;
import org.aya.prettier.BasePrettier;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.Jdg;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.ClassCall;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.core.term.xtt.DimTyTerm;
import org.aya.syntax.core.term.xtt.EqTerm;
import org.aya.syntax.ref.LocalCtx;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.MapLocalCtx;
import org.aya.syntax.ref.MetaVar;
import org.aya.syntax.telescope.AbstractTele;
import org.aya.tyck.error.ClassError;
import org.aya.tyck.error.LicitError;
import org.aya.tyck.tycker.AppTycker;
import org.aya.util.ForLSP;
import org.aya.util.Ordering;
import org.aya.util.position.SourcePos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class ArgsComputer {
  // arguments
  public final @NotNull ExprTycker tycker;
  public final @NotNull SourcePos pos;
  public final @NotNull ImmutableSeq<Expr.NamedArg> args;
  public final @Closed @NotNull AbstractTele params;

  // internal state
  private int argIx = 0;
  private int paramIx = 0;
  private @Nullable Term firstTy = null;
  private final @NotNull Term @NotNull [] result;
  private @Closed Param param;

  /// How many implicit arguments have been inserted in the beginning of the application
  @ForLSP
  private int implicitPrefixLength = -1;

  public ArgsComputer(
    @NotNull ExprTycker tycker,
    @NotNull SourcePos pos,
    @NotNull ImmutableSeq<Expr.NamedArg> args,
    @NotNull AbstractTele params
  ) {
    this.tycker = tycker;
    this.pos = pos;
    this.args = args;
    this.params = params;

    this.result = new Term[params.telescopeSize()];
  }

  private void onParamTyck(@NotNull Term wellTyped) {
    if (paramIx == 0) firstTy = param.type();
    result[paramIx] = wellTyped;
    paramIx++;
  }

  private @NotNull Term insertImplicit(@NotNull Param param, @NotNull SourcePos pos) {
    if (param.type() instanceof ClassCall clazz) {
      var unifier = tycker.unifier(pos, Ordering.Eq);
      var thises = tycker.instanceSet
        .find(clazz, unifier)
        .toSeq();

      if (thises.isEmpty()) {
        tycker.fail(new ClassError.InstanceNotFound(pos, clazz));
        return new ErrorTerm(_ -> BasePrettier.refVar(clazz.ref()));
      } else if (thises.sizeEquals(1)) {
        // TODO: garbage code, fix it
        return switch (thises.getAny()) {
          case Instance.Global global -> throw new UnsupportedOperationException("TODO");
          case Instance.Local local -> local.ref();
        };
      } else {
        var meta = tycker.freshMeta(param.name(), pos,
          new MetaVar.OfType.ClassType(clazz, thises, tycker.localCtx()), false);
        // If there is no implicit argument for the classifying field,
        //  we generate a metavariable for it.
        int knownSize = clazz.args().size();
        int requiredSize = clazz.ref().classifyingIndex() + 1;
        if (knownSize >= requiredSize) {
          return meta;
        }
        var untilClassifying = new Closure.Jit[requiredSize - knownSize];
        for (int i = 0; i < untilClassifying.length; i++) {
          var member = clazz.ref().members().get(knownSize + i);
          var arg = tycker.freshMeta(member.name(), pos,
            new MetaVar.OfType.Default(member.signature().makePi()), false);
          untilClassifying[i + knownSize] = new Closure.Jit(self ->
            AppTerm.make(arg, self));
        }
        return new ClassCastTerm(clazz.ref(), meta,
          ImmutableSeq.empty(),
          ImmutableArray.Unsafe.wrap(untilClassifying));
      }
    } else {
      return tycker.mockTerm(param, pos);
    }
  }

  static @Closed @NotNull Jdg generateApplication(
    @NotNull ExprTycker tycker,
    @NotNull ImmutableSeq<Expr.NamedArg> args, @Closed Jdg start
  ) throws ExprTycker.NotPi {
    return args.foldLeftChecked(start, (acc, arg) -> {
      if (arg.name() != null || !arg.explicit()) tycker.fail(new LicitError.BadNamedArg(arg));
      switch (tycker.whnf(acc.type())) {
        case DepTypeTerm(var kind, var piParam, var body) when kind == DTKind.Pi -> {
          var wellTy = tycker.inherit(arg.arg(), piParam).wellTyped();
          return new Jdg.Default(AppTerm.make(acc.wellTyped(), wellTy), body.apply(wellTy));
        }
        case EqTerm eq -> {
          var wellTy = tycker.inherit(arg.arg(), DimTyTerm.INSTANCE).wellTyped();
          return new Jdg.Default(eq.makePApp(acc.wellTyped(), wellTy), eq.appA(wellTy));
        }
        case MetaCall metaCall -> {
          // dom is solved immediately by the `inherit` below
          var pi = metaCall.asDt(tycker::whnf, "", "_cod", DTKind.Pi);
          if (pi == null) throw new ExprTycker.NotPi(acc.type());
          var argJdg = tycker.inherit(arg.arg(), pi.param());
          var cod = pi.body().apply(argJdg.wellTyped());
          tycker.unifier(metaCall.ref().pos(), Ordering.Eq).compare(metaCall, pi, null);
          return new Jdg.Default(AppTerm.make(acc.wellTyped(), argJdg.wellTyped()), cod);
        }
        case Term otherwise -> throw new ExprTycker.NotPi(otherwise);
      }
    });
  }

  private @Closed @NotNull Jdg kon(@NotNull AppTycker.TeleChecker k, @Nullable LocalCtx ctx) {
    var extraParams = ctx == null ? ImmutableSeq.<LocalVar>empty() : ctx.extractLocal().toSeq();
    // maybe we can eliminate a subscope when ctx is null, but I am 2 lazy.
    try (var _ = tycker.subLocalCtx()) {
      if (ctx != null) {
        tycker.localCtx().putAll(ctx);
      }

      // TODO: fix dblity
      return k.check(result, firstTy, extraParams);
    }
  }

  @NotNull Jdg boot(@NotNull AppTycker.TeleChecker k) throws ExprTycker.NotPi {
    while (argIx < args.size() && paramIx < params.telescopeSize()) {
      var arg = args.get(argIx);
      // dblity inherits from params
      param = params.telescopeRich(paramIx, result);
      // Implicit insertion
      if (arg.explicit() != param.explicit()) {
        if (!arg.explicit()) {
          tycker.fail(new LicitError.BadImplicitArg(arg));
          break;
        } else if (arg.name() == null) {
          // here, arg.explicit() == true and param.explicit() == false
          onParamTyck(insertImplicit(param, arg.sourcePos()));
          continue;
        }
      }
      if (arg.name() != null && !param.nameEq(arg.name())) {
        onParamTyck(insertImplicit(param, arg.sourcePos()));
        continue;
      }
      // If it's the first encounter of an argument, set implicitPrefixLength
      if (implicitPrefixLength == -1)
        implicitPrefixLength = paramIx;
      var what = tycker.inherit(arg.arg(), param.type());
      onParamTyck(what.wellTyped());
      // consume argument
      argIx++;
    }

    // now: ! (argIx < args.size()) || ! (paramIx < params.telescopeSize())

    // Trailing implicits
    while (paramIx < params.telescopeSize()) {
      if (params.telescopeLicit(paramIx)) break;
      param = params.telescopeRich(paramIx, result);
      onParamTyck(insertImplicit(param, pos));
    }

    if (argIx < args.size()) {
      // thus ! paramIx < params.telescopeSize(), that means we run out all parameters,
      // but remains arguments.
      return generateApplication(tycker, args.drop(argIx), kon(k, null));
    }

    var extraParams = new MapLocalCtx();
    while (paramIx < params.telescopeSize()) {
      // we run out all arguments, but the call is not a full call for now
      param = params.telescopeRich(paramIx, result);
      var atarashiVar = LocalVar.generate(param.name());
      extraParams.put(atarashiVar, param.type());
      onParamTyck(new FreeTerm(atarashiVar));
    }

    var generated = kon(k, extraParams);

    // elaborate non-full application to full application
    if (!extraParams.isEmpty()) {
      // if extraParams.isNotEmpty, then `localCtx, extraParams |- generated`
      for (var var : extraParams.extractLocal().reversed()) {
        var ty = extraParams.getLocal(var).get();
        generated = new Jdg.Default(
          new LamTerm(generated.wellTyped().bind(var)),
          new DepTypeTerm(DTKind.Pi, ty, generated.type().bind(var))
        );
      }
    }

    return generated;
  }

  public @NotNull Term headType() {
    if (implicitPrefixLength == -1) implicitPrefixLength = 0;
    var prefix = Arrays.copyOfRange(result, 0, implicitPrefixLength);
    return params.makePi(ImmutableArray.Unsafe.wrap(prefix));
  }
}
