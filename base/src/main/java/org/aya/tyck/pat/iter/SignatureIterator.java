// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat.iter;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.immutable.primitive.ImmutableBooleanSeq;
import kala.collection.mutable.MutableList;
import org.aya.syntax.core.term.DepTypeTerm;
import org.aya.syntax.core.term.Param;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.telescope.AbstractTele;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Objects;

public class SignatureIterator extends PusheenIterator<Param, Term> {
  public static @NotNull Pusheenable<Param, @NotNull Term> makePusheen(@NotNull DepTypeTerm.UnpiRaw unpi) {
    if (unpi.params().isEmpty()) {
      return new Const<>(unpi.body());
    } else {
      return new PiPusheen(unpi);

    }
  }

  public static @NotNull SignatureIterator make(
    @NotNull ImmutableSeq<Param> telescope,
    @NotNull DepTypeTerm.UnpiRaw unpi,
    @NotNull ImmutableSeq<LocalVar> teleVars,
    @NotNull ImmutableSeq<LocalVar> elims
  ) {
    if (elims.isEmpty()) {
      return make(telescope, unpi);
    } else {
      assert telescope.size() == teleVars.size();
      var bElims = teleVars.view()
        .map(elims::contains)
        .collect(ImmutableBooleanSeq.factory());

      return new SignatureIterator(telescope, new Const<>(unpi.makePi()), bElims);
    }
  }

  public static @NotNull SignatureIterator make(
    @NotNull ImmutableSeq<Param> telescope,
    @NotNull DepTypeTerm.UnpiRaw unpi
  ) {
    return new SignatureIterator(telescope, makePusheen(unpi), null);
  }

  public final @Nullable ImmutableBooleanSeq elims;
  private final @NotNull MutableList<Param> consumed = MutableList.create();
  private int teleIndex = 0;

  public SignatureIterator(ImmutableSeq<Param> tele, @NotNull Pusheenable<Param, Term> cat, @Nullable ImmutableBooleanSeq elims) {
    this(tele.iterator(), cat, elims);
  }

  public SignatureIterator(Iterator<Param> iter, @NotNull Pusheenable<Param, Term> cat, @Nullable ImmutableBooleanSeq elims) {
    super(iter, cat);
    this.elims = elims;
  }

  @Override public @NotNull Pusheenable<Param, Term> body() {
    return Objects.requireNonNull(super.body());
  }

  @Override public Param next() {
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
