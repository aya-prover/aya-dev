// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete;

import org.aya.api.error.CollectingReporter;
import org.aya.api.error.SourcePos;
import org.aya.concrete.resolve.context.ExampleContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract sealed class Sample implements Stmt {
  private final @NotNull Signatured delegate;
  public @Nullable ExampleContext ctx = null;

  public interface Visitor<P, R> {
    R visitExample(@NotNull Working example, P p);
    R visitCounterexample(@NotNull Counter example, P p);
  }

  public Sample(@NotNull Signatured delegate) {
    this.delegate = delegate;
  }

  @Override public @NotNull SourcePos sourcePos() {
    return delegate.sourcePos();
  }

  @Override public @NotNull Accessibility accessibility() {
    return Accessibility.Private;
  }

  abstract <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p);

  @Override public <P, R> R doAccept(Stmt.@NotNull Visitor<P, R> visitor, P p) {
    return doAccept((Visitor<? super P, ? extends R>) visitor, p);
  }

  public static final class Working extends Sample {
    public Working(@NotNull Signatured delegate) {
      super(delegate);
    }

    @Override <P, R> R doAccept(Sample.@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitExample(this, p);
    }
  }

  public static final class Counter extends Sample {
    public final @NotNull CollectingReporter reporter;

    public Counter(@NotNull Signatured delegate, @NotNull CollectingReporter reporter) {
      super(delegate);
      this.reporter = reporter;
    }

    @Override <P, R> R doAccept(Sample.@NotNull Visitor<P, R> visitor, P p) {
      return visitor.visitCounterexample(this, p);
    }
  }
}
