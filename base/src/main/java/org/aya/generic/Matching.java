// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.generic;

import kala.collection.immutable.ImmutableSeq;
import org.aya.api.error.SourcePos;
import org.aya.concrete.Expr;
import org.aya.concrete.Pattern;
import org.aya.concrete.visitor.ConcreteDistiller;
import org.aya.core.pat.Pat;
import org.aya.core.term.Term;
import org.aya.core.visitor.CoreDistiller;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Docile;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * @param <Matcher> {@link Pat} or {@link Pattern}
 * @param <Body>    {@link Term} or {@link Expr}
 * @author ice1000
 */
public record Matching<Matcher extends Docile, Body extends Docile>(
  @NotNull SourcePos sourcePos,
  @NotNull ImmutableSeq<Matcher> patterns,
  @NotNull Body body
) implements Docile {
  @SuppressWarnings("unchecked") @Override public @NotNull Doc toDoc() {
    var first = patterns.firstOption();
    if (first.isDefined()) {
      var matcher = first.get();
      if (matcher instanceof Pat && body instanceof Term)
        return CoreDistiller.INSTANCE.matchy((Matching<Pat, Term>) this);
      else if (matcher instanceof Pattern && body instanceof Expr)
        return ConcreteDistiller.INSTANCE.matchy((Matching<Pattern, Expr>) this);
    }
    return Doc.cat(Doc.cat(patterns.view().map(Docile::toDoc)), Doc.symbol(" => "), body.toDoc());
  }

  public @NotNull <NoBody extends Docile> Matching<Matcher, NoBody> mapBody(@NotNull Function<Body, NoBody> f) {
    return new Matching<>(sourcePos, patterns, f.apply(body));
  }
}
