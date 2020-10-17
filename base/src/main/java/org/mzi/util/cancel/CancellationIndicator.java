package org.mzi.util.cancel;

public interface CancellationIndicator {
  boolean isCanceled();
  void cancel();

  default void checkCanceled() throws CancellationException {
    if (isCanceled()) throw new CancellationException();
  }
}
