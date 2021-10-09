// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.tyck;

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
