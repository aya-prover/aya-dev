// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.compile;

import kala.collection.Seq;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableArrayList;
import kala.control.Result;
import org.aya.generic.State;
import org.aya.syntax.core.def.ConDefLike;
import org.aya.syntax.core.def.DataDefLike;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.telescope.JitTele;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnknownNullability;

public abstract non-sealed class JitCon extends JitTele implements ConDefLike {
  public final JitData dataType;
  private final boolean hasEq;
  private final int selfTeleSize;
  private @UnknownNullability ImmutableSeq<Param> selfTele;

  protected JitCon(
    int telescopeSize, boolean[] telescopeLicit, String[] telescopeName,
    JitData dataType, int selfTeleSize, boolean hasEq
  ) {
    super(telescopeSize, telescopeLicit, telescopeName);
    this.dataType = dataType;
    this.hasEq = hasEq;
    this.selfTeleSize = selfTeleSize;
  }

  /// Whether this constructor is available of data type.
  /// The default impl is for non-indexed constructors, where it's always available.
  ///
  /// @param args the argument to the data type
  /// @return a match result, a sequence of substitution if success
  public @NotNull Result<ImmutableSeq<Term>, State> isAvailable(@NotNull ImmutableSeq<Term> args) {
    return Result.ok(args);
  }

  @Override public boolean hasEq() { return hasEq; }
  @Override public @NotNull Term equality(Seq<Term> args, boolean is0) { throw new Panic("Not an HIT"); }
  @Override public @NotNull DataDefLike dataRef() { return dataType; }
  @Override public int selfTeleSize() { return selfTeleSize; }
  @Override public int ownerTeleSize() { return telescopeSize - selfTeleSize; }
  @Override public @NotNull ImmutableSeq<Param> selfTele(@NotNull ImmutableSeq<Term> ownerArgs) {
    if (selfTele == null) {
      var ownerTeleSize = ownerTeleSize();
      var fullTele = MutableArrayList.<FreeTerm>create(telescopeSize);
      for (int i = 0; i < ownerTeleSize; ++i) {
        fullTele.append(new FreeTerm(new LocalVar("JitCon" + i)));
      }

      var selfTele = MutableArrayList.<Param>create(selfTeleSize);

      for (int i = 0; i < selfTeleSize; ++i) {
        var realIdx = ownerTeleSize + i;
        var name = telescopeNames[realIdx];
        var licit = telescopeLicit[realIdx];
        var type = telescope(realIdx, Seq.narrow(fullTele));
        selfTele.append(new Param(name, type, licit));
        fullTele.append(new FreeTerm(new LocalVar("JitCon" + realIdx)));
      }

      // now bind all free variable
      selfTele.replaceAllIndexed((i, p) ->
        p.descent(type ->
          type.bindTele(fullTele.view().map(FreeTerm::name)
            .slice(0, ownerTeleSize + i))));

      this.selfTele = selfTele.toImmutableSeq();
    }

    return Param.substTele(selfTele.view(), ownerArgs.view()).toImmutableSeq();
  }
}
