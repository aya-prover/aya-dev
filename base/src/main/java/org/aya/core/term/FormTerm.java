// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.term;

import kala.collection.SeqLike;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.generic.Cube;
import org.aya.guest0x0.cubical.Restr;
import org.jetbrains.annotations.NotNull;

/**
 * Formation rules.
 *
 * @author ice1000
 */
public sealed interface FormTerm extends Term {
  /**
   * @author re-xyr, kiva, ice1000
   */
  record Pi(@NotNull Term.Param param, @NotNull Term body) implements FormTerm, StableWHNF {

    public @NotNull Term substBody(@NotNull Term term) {
      return body.subst(param.ref(), term);
    }

    public @NotNull Term parameters(@NotNull MutableList<Term.@NotNull Param> params) {
      params.append(param);
      var t = body;
      while (t instanceof Pi pi) {
        params.append(pi.param);
        t = pi.body;
      }
      return t;
    }

    public static @NotNull Term make(@NotNull SeqLike<@NotNull Param> telescope, @NotNull Term body) {
      return telescope.view().foldRight(body, Pi::new);
    }
  }

  static @NotNull Term unpi(@NotNull Term term, @NotNull MutableList<Term.Param> params) {
    while (term instanceof Pi pi) {
      params.append(pi.param);
      term = pi.body;
    }
    return term;
  }

  /**
   * @author re-xyr
   */
  record Sigma(@NotNull ImmutableSeq<@NotNull Param> params) implements FormTerm, StableWHNF {
  }

  enum SortKind {
    Type, Set, Prop, ISet;

    @Override public String toString() {
      return this.name();
    }

    public boolean hasLevel() {
      return this == Type || this == Set;
    }

    public @NotNull SortKind max(@NotNull SortKind other) {
      if (this == Set || other == Set) return Set;
      if (this == Type || other == Type) return Type;
      // Prop or ISet
      return this == other ? this : Type;
    }
  }

  /**
   * @author ice1000
   */
  sealed interface Sort extends FormTerm, StableWHNF {
    int lift();
    @NotNull FormTerm.SortKind kind();
    @NotNull FormTerm.Sort succ();

    static @NotNull Sort create(@NotNull FormTerm.SortKind kind, int lift) {
      return switch (kind) {
        case Type -> new Type(lift);
        case Set -> new Set(lift);
        case Prop -> Prop.INSTANCE;
        case ISet -> ISet.INSTANCE;
      };
    }

    default @NotNull Sort max(@NotNull Sort other) {
      return Sort.create(this.kind().max(other.kind()), Math.max(this.lift(), other.lift()));
    }
  }

  record Type(@Override int lift) implements Sort {
    public static final @NotNull FormTerm.Type ZERO = new Type(0);

    @Override public @NotNull FormTerm.SortKind kind() {
      return SortKind.Type;
    }

    @Override public @NotNull FormTerm.Type succ() {
      return new FormTerm.Type(lift + 1);
    }
  }

  record Set(@Override int lift) implements Sort {
    public static final @NotNull FormTerm.Set ZERO = new Set(0);

    @Override public @NotNull FormTerm.SortKind kind() {
      return SortKind.Set;
    }

    @Override
    public @NotNull FormTerm.Set succ() {
      return new FormTerm.Set(lift + 1);
    }
  }

  final class Prop implements Sort {
    public static final @NotNull Prop INSTANCE = new Prop();

    private Prop() {
    }

    @Override public int lift() {
      return 0;
    }

    @Override public @NotNull FormTerm.SortKind kind() {
      return SortKind.Prop;
    }

    @Override public @NotNull FormTerm.Type succ() {
      return new FormTerm.Type(1);
    }
  }

  final class ISet implements Sort {
    public static final @NotNull ISet INSTANCE = new ISet();

    private ISet() {

    }

    @Override public int lift() {
      return 0;
    }

    @Override public @NotNull FormTerm.SortKind kind() {
      return SortKind.ISet;
    }

    @Override public @NotNull FormTerm.Set succ() {
      return new FormTerm.Set(1);
    }
  }

  /** partial type */
  record PartTy(@NotNull Term type, @NotNull Restr<Term> restr) implements FormTerm {}

  /** generalized path type */
  record Path(@NotNull Cube<Term> cube) implements FormTerm, StableWHNF {}
}
