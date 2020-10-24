// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.util.cancel;

/**
 * @author ice1000
 */
public class CancellationException extends RuntimeException {
  public CancellationException() {
  }

  public CancellationException(String message) {
    super(message);
  }

  public CancellationException(String message, Throwable cause) {
    super(message, cause);
  }

  public CancellationException(Throwable cause) {
    super(cause);
  }

  public CancellationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
