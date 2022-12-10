// Copyright (c) 2020-2022 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.util.distill;


import java.util.HashMap;
import java.util.Map;

/**
 * @author ice1000
 */
public abstract class DistillerOptions {
  public final Map<Key, Boolean> map = new HashMap<>();

  {
    reset();
  }

  public abstract void reset();

  public interface Key {
  }
}
