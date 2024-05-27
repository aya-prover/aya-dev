// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.prettier;

import org.aya.prettier.BasePrettier.Usage.Ref;
import org.aya.syntax.core.term.FreeTerm;
import org.aya.syntax.core.term.Term;
import org.aya.syntax.core.term.call.MetaCall;
import org.aya.syntax.ref.LocalVar;
import org.aya.syntax.ref.MetaVar;
import org.aya.util.Pair;
import org.jetbrains.annotations.NotNull;

/**
 * It is in this module instead of somewhere in base because it's needed by {@link CorePrettier},
 * which is in the syntax module.
 */
public record FindUsage(@NotNull Ref ref, @NotNull Accumulator accumulator) {
  public FindUsage(@NotNull Ref ref) {this(ref, new Accumulator());}
  // Z \oplus Z
  public static class Accumulator {
    public int metaUsage;
    public int termUsage;
    boolean inMeta = false;
    public Accumulator(int metaUsage, int termUsage) {
      this.metaUsage = metaUsage;
      this.termUsage = termUsage;
    }
    public Accumulator() {this(0, 0);}

    public void found() {
      if (inMeta) metaUsage++;
      else termUsage++;
    }

    // Z \oplus Z -> Z
    // omg, this is Z_{Integer.MAX_VALUE * 2}
    public int homomorphism() {return metaUsage + termUsage;}
  }

  public void find(int index, @NotNull Term term) {
    switch (new Pair<>(term, ref)) {
      case Pair(FreeTerm(_), Ref.AnyFree _) -> accumulator.found();
      case Pair(FreeTerm(var var), Ref.Free(var fvar)) when var == fvar -> accumulator.found();
      case Pair(MetaCall meta, Ref.Meta(var fvar)) when meta.ref() == fvar -> accumulator.found();
      default -> {
        var before = accumulator.inMeta;
        if (term instanceof MetaCall) accumulator.inMeta = true;
        term.descent((l, t) -> {
          find(index + l, t);
          return t;
        });
        accumulator.inMeta = before;
      }
    }
  }

  public int apply(int index, @NotNull Term term) {
    find(index, term);
    return accumulator.homomorphism();
  }

  public static int free(Term t, LocalVar l) {
    return new FindUsage(new Ref.Free(l)).apply(0, t);
  }
  public static int meta(Term t, MetaVar l) {
    return new FindUsage(new Ref.Meta(l)).apply(0, t);
  }
  public static @NotNull Accumulator anyFree(Term t) {
    var findUsage = new FindUsage(Ref.AnyFree.INSTANCE);
    findUsage.find(0, t);
    return findUsage.accumulator;
  }
}
