// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.def;

import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.decl.TeleDecl;
import org.aya.core.pat.Pat;
import org.aya.core.term.SortTerm;
import org.aya.core.term.Term;
import org.aya.guest0x0.cubical.Partial;
import org.aya.ref.DefVar;
import org.aya.util.Arg;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * @author ice1000, kiva
 */
public final class CtorDef extends SubLevelDef {
  public final @NotNull DefVar<DataDef, TeleDecl.DataDecl> dataRef;
  public final @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref;
  public final @NotNull Partial.Split<Term> clauses;
  public final @NotNull ImmutableSeq<Arg<Pat>> pats;

  /**
   * @param ownerTele See "/note/glossary.md"
   * @param selfTele  Ditto
   */
  public CtorDef(
    @NotNull DefVar<DataDef, TeleDecl.DataDecl> dataRef, @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref,
    @NotNull ImmutableSeq<Arg<Pat>> pats,
    @NotNull ImmutableSeq<Term.Param> ownerTele, @NotNull ImmutableSeq<Term.Param> selfTele,
    @NotNull Partial.Split<Term> clauses, @NotNull Term result, boolean coerce
  ) {
    super(ownerTele, selfTele, result, coerce);
    ref.core = this;
    this.dataRef = dataRef;
    this.clauses = clauses;
    this.ref = ref;
    this.pats = pats;
  }

  public @NotNull DefVar<CtorDef, TeleDecl.DataCtor> ref() {
    return ref;
  }

  public @NotNull CtorDef update(@NotNull ImmutableSeq<Arg<Pat>> pats, @NotNull ImmutableSeq<Term.Param> selfTele, @NotNull Term result, @NotNull Partial.Split<Term> clauses) {
    return pats.sameElements(this.pats, true) && selfTele.sameElements(this.selfTele, true) && result == this.result && clauses == this.clauses
      ? this : new CtorDef(dataRef, ref, pats, ownerTele, selfTele, clauses, result, coerce);
  }

  @Override
  public @NotNull CtorDef descent(@NotNull UnaryOperator<Term> f, @NotNull UnaryOperator<Pat> g) {
    return update(pats.map(a -> a.descent(g)), selfTele.map(p -> p.descent(f)), f.apply(result), clauses.fmap(f));
  }

  @Override public @NotNull ImmutableSeq<Term.Param> telescope() {
    return fullTelescope().toImmutableSeq();
  }

  private @NotNull SortTerm dataResult() {
    return dataRef.concrete == null ? dataRef.core.result
      : Objects.requireNonNull(dataRef.concrete.signature).result();
  }

  public boolean inProp() {
    return dataResult().isProp();
  }
}
