// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.core.def;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.stmt.decl.DataCon;
import org.aya.syntax.core.pat.Pat;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.xtt.EqTerm;
import org.aya.syntax.ref.DefVar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000, kiva
 */
public final class ConDef extends SubLevelDef {
  public final @NotNull DataDefLike dataRef;
  public final @NotNull DefVar<ConDef, DataCon> ref;
  public final @NotNull ImmutableSeq<Pat> pats;
  public final @Nullable EqTerm equality;

  /**
   * @param ownerTele See "/note/glossary.md"
   * @param selfTele  Ditto
   */
  public ConDef(
    @NotNull DataDefLike dataRef, @NotNull DefVar<ConDef, DataCon> ref,
    @NotNull ImmutableSeq<Pat> pats, @Nullable EqTerm equality, @NotNull ImmutableSeq<Param> ownerTele,
    @NotNull ImmutableSeq<Param> selfTele, @NotNull Term result, boolean coerce
  ) {
    super(ownerTele, selfTele, result, coerce);
    this.pats = pats;
    this.equality = equality;
    ref.initialize(this);
    this.dataRef = dataRef;
    this.ref = ref;
  }

  @Override public @NotNull DefVar<ConDef, DataCon> ref() { return ref; }
  @Override public @NotNull ImmutableSeq<Param> telescope() {
    return fullTelescope().toImmutableSeq();
  }

  public static final class Delegate extends TyckAnyDef<ConDef> implements ConDefLike {
    public Delegate(@NotNull DefVar<ConDef, ?> ref) { super(ref); }
    @Override public boolean hasEq() { return core().equality != null; }
    @Override public @NotNull Term equality(Seq<Term> args, boolean is0) {
      var equality = core().equality;
      assert equality != null;
      return (is0 ? equality.a() : equality.b()).instTele(args.view());
    }
    @Override public int selfTeleSize() { return core().selfTele.size(); }
    @Override public int ownerTeleSize() { return core().ownerTele.size(); }
    @Override public @NotNull ImmutableSeq<Param> selfTele(@NotNull ImmutableSeq<Term> ownerArgs) {
      return Param.substTele(core().selfTele.view(), ownerArgs.view()).toImmutableSeq();
    }

    @Override public @NotNull DataDefLike dataRef() { return core().dataRef; }
  }
}
