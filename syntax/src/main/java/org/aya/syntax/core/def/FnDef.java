// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import kala.control.Either;
import org.aya.generic.Modifier;
import org.aya.syntax.concrete.stmt.decl.FnDecl;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.function.Function;

public record FnDef(
  @NotNull DefVar<FnDef, FnDecl> ref,
  @NotNull EnumSet<Modifier> modifiers,
  @NotNull Either<Term, ImmutableSeq<Term.Matching>> body
) implements TopLevelDef {
  public FnDef { ref.core = this; }

  public static <T> Function<Either<Term, ImmutableSeq<Term.Matching>>, T>
  factory(Function<Either<Term, ImmutableSeq<Term.Matching>>, T> function) {
    return function;
  }

  public boolean is(@NotNull Modifier mod) { return modifiers.contains(mod); }
  public static final class Delegate extends TyckAnyDef<FnDef> implements FnDefLike {
    public Delegate(@NotNull DefVar<FnDef, ?> ref) { super(ref); }
  }
}
