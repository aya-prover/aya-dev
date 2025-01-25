// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import java.util.EnumSet;

import kala.control.Either;
import org.aya.generic.Modifier;
import org.aya.syntax.concrete.stmt.decl.FnDecl;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

public record FnDef(
  @NotNull DefVar<FnDef, FnDecl> ref,
  @NotNull EnumSet<Modifier> modifiers,
  @NotNull Either<Term, FnClauseBody> body
) implements TopLevelDef {
  public FnDef { ref.initialize(this); }

  public boolean is(@NotNull Modifier mod) { return modifiers.contains(mod); }
  public static final class Delegate extends TyckAnyDef<FnDef> implements FnDefLike {
    public Delegate(@NotNull DefVar<FnDef, ?> ref) { super(ref); }
    @Override public boolean is(@NotNull Modifier mod) {
      var core = ref.core;
      if (core != null) return core.is(mod);
      return ((FnDecl) ref.concrete).modifiers.contains(mod);
    }
  }
}
