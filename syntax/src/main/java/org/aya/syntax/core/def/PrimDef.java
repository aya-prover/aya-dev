// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.term.DTKind;
import org.aya.syntax.concrete.stmt.decl.PrimDecl;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.annotation.Closed;
import org.aya.syntax.core.term.DepTypeTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record PrimDef(
  @Override @NotNull DefVar<@NotNull PrimDef, @NotNull PrimDecl> ref,
  @NotNull ImmutableSeq<Param> telescope,
  @NotNull Term result, PrimDef.@NotNull ID id
) implements TopLevelDef {
  public PrimDef { ref.initialize(this); }
  public PrimDef(@NotNull DefVar<@NotNull PrimDef, @NotNull PrimDecl> ref, @NotNull Term result, @NotNull ID name) {
    this(ref, ImmutableSeq.empty(), result, name);
  }

  @Override public @NotNull ImmutableSeq<Param> telescope() {
    if (telescope.isEmpty()) return telescope;
    var signature = ref.signature;
    if (signature != null) return signature.params();
    return telescope;
  }

  @Override public @NotNull Term result() {
    var signature = ref.signature;
    if (signature != null) return signature.result();
    return result;
  }

  /// Let `A` be argument, then `A i -> A j`. Handles index shifting.
  public static @NotNull DepTypeTerm familyI2J(Closure term, @Closed Term i, @Closed Term j) {
    return new DepTypeTerm(DTKind.Pi, term.apply(i), Closure.mkConst(term.apply(j)));
  }

  public enum ID {
    STRING("String"),
    STRCONCAT("strcat"),
    I("I"),
    PATH("Path"),
    PARTIAL("Partial"),
    COE("coe"),
    HCOMP("hcom");

    public final @NotNull @NonNls String id;
    @Override public String toString() { return id; }

    public static @Nullable ID find(@NotNull String id) {
      for (var value : PrimDef.ID.values())
        if (Objects.equals(value.id, id)) return value;
      return null;
    }

    ID(@NotNull String id) { this.id = id; }
  }

  public static final class Delegate extends TyckAnyDef<PrimDef> implements PrimDefLike {
    public Delegate(@NotNull DefVar<PrimDef, ?> ref) { super(ref); }
    @Override public @NotNull PrimDef.ID id() { return core().id; }
  }
}
