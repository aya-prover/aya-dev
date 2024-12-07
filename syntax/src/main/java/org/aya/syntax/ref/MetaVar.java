// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.ref;

import kala.collection.SeqView;
import kala.collection.immutable.ImmutableSeq;
import org.aya.generic.AyaDocile;
import org.aya.generic.term.DTKind;
import org.aya.generic.term.SortKind;
import org.aya.pretty.doc.Doc;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.term.DepTypeTerm;
import org.aya.syntax.core.term.SortTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.util.error.SourcePos;
import org.aya.util.prettier.PrettierOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * if the <code>args</code> of the {@link MetaCall} is larger than ctxSize,
 * then in case there is {@link OfType}, we will need to type check the argument
 * and check the solution against the iterated <strong>codomain</strong> instead of the type itself.
 *
 * @param ctxSize size of the original context.
 * @param pos     error report of this MetaCall will be associated with this position.
 * @see MetaCall
 */
public record MetaVar(
  @Override @NotNull String name,
  @NotNull SourcePos pos,
  int ctxSize, @NotNull Requirement req,
  boolean isUser
) implements AnyVar {
  @Override public boolean equals(@Nullable Object o) { return this == o; }
  @Override public int hashCode() { return System.identityHashCode(this); }

  public @NotNull MetaCall asPiDom(@NotNull SortTerm sort, @NotNull ImmutableSeq<Term> args) {
    assert req == Misc.IsType;
    var typed = new MetaVar(name, pos, ctxSize, new PiDom(sort), isUser);
    return new MetaCall(typed, args);
  }

  /** @see MetaCall#asDt(UnaryOperator, String, String, DTKind) */
  public @Nullable DepTypeTerm
  asDt(UnaryOperator<Term> whnf, String dom, String cod, DTKind kind, ImmutableSeq<Term> args) {
    var newReq = req.asDepTypeReq(whnf);
    if (newReq == null) return null;
    var domMeta = new MetaVar(name + dom, pos, ctxSize, newReq, false);
    var codMeta = new MetaVar(name + cod, pos, ctxSize + 1, Misc.IsType, false);
    var domDT = new MetaCall(domMeta, args);
    var codDT = new Closure.Jit(t -> new MetaCall(codMeta, args.appended(t)));
    return new DepTypeTerm(kind, domDT, codDT);
  }

  public sealed interface Requirement extends AyaDocile {
    Requirement bind(SeqView<LocalVar> vars);
    default @Nullable Requirement asDepTypeReq(@NotNull UnaryOperator<Term> whnf) { return this; }
  }
  public enum Misc implements Requirement {
    Whatever,
    IsType,
    ;
    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      return switch (this) {
        case Whatever -> Doc.plain("Nothing!");
        case IsType -> Doc.sep(Doc.plain("_"), Doc.symbols(":", "?"));
      };
    }
    @Override public Misc bind(SeqView<LocalVar> vars) { return this; }
    @Override public @NotNull Requirement asDepTypeReq(@NotNull UnaryOperator<Term> whnf) {
      if (this == Whatever) return IsType;
      else return this;
    }
  }
  /**
   * @param type hopefully in the closed context.
   *             Upon creation, it will be bound with all the local vars.
   */
  public record OfType(@NotNull Term type) implements Requirement {
    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.symbol("?"), Doc.symbol(":"), type.toDoc(options));
    }
    @Override public OfType bind(SeqView<LocalVar> vars) {
      return new OfType(type.bindTele(vars));
    }
    @Override public @Nullable Requirement asDepTypeReq(@NotNull UnaryOperator<Term> whnf) {
      if (!(whnf.apply(type) instanceof SortTerm sort) || sort.kind() == SortKind.ISet) {
        return null;
      }
      return this;
    }
  }
  /**
   * The meta variable is the domain of a pi type which is of a known type.
   */
  public record PiDom(@NotNull SortTerm sort) implements Requirement {
    @Override public PiDom bind(SeqView<LocalVar> vars) { return this; }
    @Override public @NotNull Doc toDoc(@NotNull PrettierOptions options) {
      return Doc.sep(Doc.symbols("?", "->", "_", ":"), sort.toDoc(options));
    }
  }
}
