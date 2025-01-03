// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat.iter;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableBooleanSeq;
import kala.collection.mutable.MutableList;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.telescope.AbstractTele;
import org.aya.syntax.telescope.Signature;
import org.aya.tyck.TyckState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Objects;

public class SignatureIterator extends PusheenIterator<Param, Term> {
  public static @NotNull SignatureIterator make(@NotNull TyckState state, @NotNull AbstractTele.Locns signature) {
    return new SignatureIterator(signature.telescope().iterator(), new PiPusheen(state, signature.result()), null);
  }

  public static @NotNull SignatureIterator make(
    @NotNull AbstractTele.Locns signature,
    @NotNull ImmutableSeq<LocalVar> teleVars,
    @NotNull ImmutableSeq<LocalVar> elims
  ) {
    assert signature.telescope().size() == teleVars.size();
    var bElims = teleVars.map(elims::contains)
      .stream()
      .collect(ImmutableBooleanSeq.factory());

    return new SignatureIterator(signature.telescope().iterator(), new ConstPusheen<>(signature.result()), bElims);
  }

  public final @Nullable ImmutableBooleanSeq elims;
  private final @NotNull MutableList<Param> consumed = MutableList.create();
  private int teleIndex = 0;

  public SignatureIterator(Iterator<Param> iter, @NotNull Pusheenable<Param, Term> cat, @Nullable ImmutableBooleanSeq elims) {
    super(iter, cat);
    this.elims = elims;
  }

  @Override
  public @NotNull Pusheenable<Param, Term> body() {
    return Objects.requireNonNull(super.body());
  }

  @Override
  public Param next() {
    var theNext = super.next();
    consumed.append(theNext);
    return theNext;
  }

  public @NotNull AbstractTele.Locns signature() {
    return new AbstractTele.Locns(consumed.toImmutableSeq(), body().body());
  }

  @Override
  protected @NotNull Param postDoPeek(@NotNull Param peeked) {
    var index = teleIndex++;
    if (elims == null || index >= elims.size()) return peeked;
    return new Param(peeked.name(), peeked.type(), elims.get(index));
  }
}
