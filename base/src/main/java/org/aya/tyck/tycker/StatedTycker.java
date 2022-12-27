// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import org.aya.concrete.stmt.Decl;
import org.aya.concrete.stmt.TeleDecl;
import org.aya.core.def.*;
import org.aya.core.term.*;
import org.aya.generic.Modifier;
import org.aya.generic.util.InternalException;
import org.aya.generic.util.NormalizeMode;
import org.aya.ref.DefVar;
import org.aya.tyck.Result;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.trace.Trace;
import org.aya.tyck.unify.TermComparator;
import org.aya.tyck.unify.Unifier;
import org.aya.util.Ordering;
import org.aya.util.error.SourcePos;
import org.aya.util.reporter.Reporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is the second base-base class of a tycker.
 * It has the zonking stuffs and basic def-call related functions.
 * Apart from that, it also deals with core term references in concrete terms.
 *
 * @author ice1000
 * @see #whnf(Term)
 * @see #defCall
 * @see #inferRef(DefVar)
 */
public abstract sealed class StatedTycker extends TracedTycker permits ConcreteAwareTycker, TermComparator {
  public final @NotNull TyckState state;

  protected StatedTycker(@NotNull Reporter reporter, @Nullable Trace.Builder traceBuilder, @NotNull TyckState state) {
    super(reporter, traceBuilder);
    this.state = state;
  }

  public @NotNull Term whnf(@NotNull Term term) {
    return term.normalize(state, NormalizeMode.WHNF);
  }

  protected final @NotNull <D extends Def, S extends Decl & Decl.Telescopic<?>> Result defCall(DefVar<D, S> defVar, Callable.Factory<D, S> function) {
    var tele = Def.defTele(defVar);
    var teleRenamed = tele.map(org.aya.core.term.Term.Param::rename);
    // unbound these abstracted variables
    Term body = function.make(defVar, 0, teleRenamed.map(org.aya.core.term.Term.Param::toArg));
    var type = PiTerm.make(tele, Def.defResult(defVar)).rename();
    if ((defVar.core instanceof FnDef fn && fn.modifiers.contains(Modifier.Inline)) || defVar.core instanceof PrimDef) {
      body = whnf(body);
    }
    return new Result.Default(LamTerm.make(teleRenamed, body), type);
  }

  @SuppressWarnings("unchecked") protected final @NotNull Result inferRef(@NotNull DefVar<?, ?> var) {
    if (var.core instanceof FnDef || var.concrete instanceof TeleDecl.FnDecl) {
      return defCall((DefVar<FnDef, TeleDecl.FnDecl>) var, FnCall::new);
    } else if (var.core instanceof PrimDef) {
      return defCall((DefVar<PrimDef, TeleDecl.PrimDecl>) var, PrimCall::new);
    } else if (var.core instanceof DataDef || var.concrete instanceof TeleDecl.DataDecl) {
      return defCall((DefVar<DataDef, TeleDecl.DataDecl>) var, DataCall::new);
    } else if (var.core instanceof StructDef || var.concrete instanceof TeleDecl.StructDecl) {
      return defCall((DefVar<StructDef, TeleDecl.StructDecl>) var, StructCall::new);
    } else if (var.core instanceof CtorDef || var.concrete instanceof TeleDecl.DataDecl.DataCtor) {
      var conVar = (DefVar<CtorDef, TeleDecl.DataDecl.DataCtor>) var;
      var tele = Def.defTele(conVar);
      var type = PiTerm.make(tele, Def.defResult(conVar)).rename();
      var telescopes = new DataDef.CtorTelescopes(conVar.core);
      return new Result.Default(telescopes.toConCall(conVar, 0), type);
    } else if (var.core instanceof FieldDef || var.concrete instanceof TeleDecl.StructField) {
      // the code runs to here because we are tycking a StructField in a StructDecl
      // there should be two-stage check for this case:
      //  - check the definition's correctness: happens here
      //  - check the field value's correctness: happens in `visitNew` after the body was instantiated
      var field = (DefVar<FieldDef, TeleDecl.StructField>) var;
      return new Result.Default(new RefTerm.Field(field), Def.defType(field));
    } else {
      final var msg = "Def var `" + var.name() + "` has core `" + var.core + "` which we don't know.";
      throw new InternalException(msg);
    }
  }

  public @NotNull Unifier unifier(@NotNull SourcePos pos, @NotNull Ordering ord, @NotNull LocalCtx ctx) {
    return new Unifier(ord, reporter, false, true, traceBuilder, state, pos, ctx);
  }
}
