// Copyright (c) 2020-2023 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli;

import org.aya.cli.repl.gk.GKReplLexer;
import org.aya.parser._AyaPsiLexer;

public class LexerPlayground {
  public static void main(String[] args) {
    var lexer = new GKReplLexer(new _AyaPsiLexer(false));
    lexer.reset("a  \n\n  b", 0);
    lexer.allTheWayDown().forEach(System.out::println);
  }
}
