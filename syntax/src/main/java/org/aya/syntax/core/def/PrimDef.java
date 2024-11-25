// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.stmt.decl.PrimDecl;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.DepTypeTerm.DTKind;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.DepTypeTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.xtt.DimTyTerm;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static org.aya.syntax.core.term.SortTerm.Type0;

public final class PrimDef implements TopLevelDef {
  public final @NotNull ImmutableSeq<Param> telescope;
  public final @NotNull Term result;
  public final @NotNull DefVar<@NotNull PrimDef, @NotNull PrimDecl> ref;
  public final @NotNull ID id;

  public PrimDef(
    @NotNull DefVar<@NotNull PrimDef, @NotNull PrimDecl> ref,
    @NotNull ImmutableSeq<Param> telescope,
    @NotNull Term result, @NotNull ID id
  ) {
    this.telescope = telescope;
    this.result = result;
    this.ref = ref;
    this.id = id;
    ref.initialize(this);
  }

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

  /** <code>I -> Type</code> */
  public static final @NotNull Term intervalToType = new DepTypeTerm(DTKind.Pi, DimTyTerm.INSTANCE, Closure.mkConst(Type0));

  /** Let A be argument, then <code>A i -> A j</code>. Handles index shifting. */
  public static @NotNull DepTypeTerm familyI2J(Closure term, Term i, Term j) {
    return new DepTypeTerm(DTKind.Pi, term.apply(i), Closure.mkConst(term.apply(j)));
  }

  public enum ID {
    STRING("String"),
    STRCONCAT("strcat"),
    I("I"),
    PATH("Path"),
    COE("coe"),
    HCOMP("hcomp");

    public final @NotNull
    @NonNls String id;

    @Override public String toString() { return id; }

    public static @Nullable ID find(@NotNull String id) {
      for (var value : PrimDef.ID.values())
        if (Objects.equals(value.id, id)) return value;
      return null;
    }

    ID(@NotNull String id) { this.id = id; }
  }

  public @NotNull DefVar<@NotNull PrimDef, @NotNull PrimDecl> ref() { return ref; }

  public static final class Delegate extends TyckAnyDef<PrimDef> implements PrimDefLike {
    public Delegate(@NotNull DefVar<PrimDef, ?> ref) { super(ref); }
    @Override public @NotNull PrimDef.ID id() { return core().id; }
  }
}
