// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.tyck.unify;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Modifiers for typing rules as functions.
 *
 * @author ice1000
 */
public interface Rule {
  /**
   * A checking typing rule.
   */
  @Retention(RetentionPolicy.SOURCE)
  @interface Check {
    /**
     * @return does this checking rule tries to work when no type is specified?
     */
    boolean partialSynth() default false;
  }

  /**
   * A synthesizing typing rule.
   */
  @Retention(RetentionPolicy.SOURCE)
  @interface Synth {
  }
}
