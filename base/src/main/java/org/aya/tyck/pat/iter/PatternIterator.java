// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck.pat.iter;

import kala.collection.immutable.ImmutableSeq;
import org.aya.syntax.concrete.Expr;
import org.aya.syntax.concrete.Pattern;
import org.aya.util.Arg;
import org.aya.util.error.WithPos;
import org.jetbrains.annotations.Nullable;

public class PatternIterator extends PusheenIterator<Arg<WithPos<Pattern>>, WithPos<Expr>> {
  public PatternIterator(ImmutableSeq<Arg<WithPos<Pattern>>> patterns, @Nullable LambdaPusheen cat) {
    super(patterns.iterator(), cat);
  }
}
