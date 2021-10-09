// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.utils;

public class PicocliUtils {
  // I couldn't find a simpler way to let Picocli automatically show candidate values. Appending this is a workaround solution.
  public static final String CANDIDATES_ON_A_NEW_LINE = "\n  Candidates: ${COMPLETION-CANDIDATES}";
}
