// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.tycker;

import org.aya.concrete.stmt.decl.ClassDecl;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.UntypedParam;
import org.aya.core.def.*;
import org.aya.core.term.*;
import org.aya.core.visitor.Subst;
import org.aya.generic.Modifier;
import org.aya.generic.util.InternalException;
import org.aya.generic.util.NormalizeMode;
import org.aya.guest0x0.cubical.CofThy;
import org.aya.guest0x0.cubical.Restr;
import org.aya.ref.DefVar;
import org.aya.tyck.Result;
import org.aya.tyck.env.LocalCtx;
import org.aya.tyck.pat.PatClassifier;
import org.aya.tyck.pat.PatternTycker;
import org.aya.tyck.trace.Trace;
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
 * @see #conOwnerSubst(ConCall)
 */
public abstract sealed class StatedTycker extends TracedTycker permits PatClassifier, MockTycker {
  public final @NotNull TyckState state;

  protected StatedTycker(@NotNull Reporter reporter, @Nullable Trace.Builder traceBuilder, @NotNull TyckState state) {
    super(reporter, traceBuilder);
    this.state = state;
  }

  public @NotNull Term whnf(@NotNull Term term) {
    return term.normalize(state, NormalizeMode.WHNF);
  }

  protected final <D extends Def, S extends TeleDecl<? extends Term>> @NotNull Result
  defCall(DefVar<D, S> defVar, Callable.Factory<D, S> function) {
    var tele = Def.defTele(defVar);
    var teleRenamed = tele.map(LamTerm::paramRenamed);
    // unbound these abstracted variables
    Term body = function.make(defVar, 0, teleRenamed.map(UntypedParam::toArg));
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
    } else if (var.core instanceof ClassDef || var.concrete instanceof ClassDecl) {
      // return defCall((DefVar<ClassDef, ClassDecl>) var, StructCall::new);
      throw new UnsupportedOperationException("TODO");
    } else if (var.core instanceof CtorDef || var.concrete instanceof TeleDecl.DataDecl.DataCtor) {
      var conVar = (DefVar<CtorDef, TeleDecl.DataDecl.DataCtor>) var;
      var tele = Def.defTele(conVar);
      var type = PiTerm.make(tele, Def.defResult(conVar)).rename();
      var telescopes = new DataDef.CtorTelescopes(conVar.core);
      return new Result.Default(telescopes.toConCall(conVar, 0), type);
    } else if (var.core instanceof ClassDef.Member || var.concrete instanceof TeleDecl.ClassMember) {
      // the code runs to here because we are checking a StructField within a StructDecl
      // TODO: this needs to be refactored to make use of instance resolution
      var field = (DefVar<ClassDef.Member, TeleDecl.ClassMember>) var;
      return new Result.Default(new RefTerm.Field(field), Def.defType(field));
    } else {
      final var msg = "Def var `" + var.name() + "` has core `" + var.core + "` which we don't know.";
      throw new InternalException(msg);
    }
  }

  public @NotNull Unifier unifier(@NotNull SourcePos pos, @NotNull Ordering ord, @NotNull LocalCtx ctx) {
    return new Unifier(ord, reporter, false, true, traceBuilder, state, pos, ctx);
  }

  /**
   * <code>Sub lr, Sub rl</code> are unused because they are solely for the purpose of unification.
   * In this case, we don't expect unification.
   */
  protected final boolean compareRestr(@NotNull Restr<Term> lhs, @NotNull Restr<Term> rhs) {
    return CofThy.conv(lhs, new Subst(), s -> CofThy.satisfied(s.restr(state, rhs)))
      && CofThy.conv(rhs, new Subst(), s -> CofThy.satisfied(s.restr(state, lhs)));
  }

  /**
   * Used for getting the subst for an inductive type's constructor's types.
   * This method handles both indexed and non-indexed constructors.
   */
  protected @NotNull Subst conOwnerSubst(@NotNull ConCall conCall) {
    return PatternTycker.mischa(conCall.head().underlyingDataCall(), conCall.ref().core, state).get();
  }
}
