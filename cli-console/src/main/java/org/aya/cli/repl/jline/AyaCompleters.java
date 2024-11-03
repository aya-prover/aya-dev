// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.cli.repl.jline;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.cli.repl.AyaRepl;
import org.aya.generic.Constants;
import org.aya.parser.AyaParserDefinitionBase;
import org.aya.parser.AyaPsiElementTypes;
import org.aya.repl.ReplParser;
import org.jetbrains.annotations.NotNull;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;
import java.util.stream.Collectors;

public interface AyaCompleters {
  @NotNull List<Candidate> KEYWORDS = ImmutableSeq.of(AyaParserDefinitionBase.KEYWORDS.getTypes())
    .view().map(IElementType::toString)
    .map(Candidate::new).collect(Collectors.toList());
  @NotNull Completer KW = (_, _, candidates) -> candidates.addAll(KEYWORDS);

  class Context implements Completer {
    private final @NotNull AyaRepl repl;
    public Context(@NotNull AyaRepl repl) { this.repl = repl; }

    /**
     * @param moduleName the module of the name that need to be completed, for example:
     *                   {@code a::b} in {@code a::b::[cursor]} and {@code a::b} in {@code a::b::c[cursor]}
     * @param fixed whether the cursor is after a {@link Constants#SCOPE_SEPARATOR}
     */
    record FixWord(@NotNull ImmutableSeq<String> moduleName, boolean fixed) { }

    /**
     * There are two possible completion cases:
     * <ul>
     *   <li>I have <code>a[cursor]</code> (or maybe <code>a::b[cursor]</code>), i.e. cursor is on a name.</li>
     *   <li>I have <code>a::[cursor]</code>, i.e. cursor is after a scope separator.</li>
     * </ul>
     * The first one is just fine, the second one we want to complete using the name before the scope separator,
     * so we need to "fix" the word by moving to the word on the left.
     *
     * @return the sequence of the words that constitute a module name.
     * If fixed, it will contain the entire sequence of names,
     * otherwise it will contain the names before the last scope separator,
     * and the last name should be used for filtering.
     */
    private @NotNull FixWord fixWord(
      @NotNull String word, int wordIndex,
      ImmutableSeq<FlexLexer.Token> tokens, String line
    ) {
      // drop the last word anyway, cause:
      // * if the line ends with `::`, we need the symbols before the last `::`,
      //   and we intend to provide things like `::A`, `::B`, etc.
      // * if the line ends with a name token, this token needs to be removed/completed
      //   because it might be the prefix of a symbol/module name.
      int lastOfThePath = wordIndex - 1;
      boolean isFixed = Constants.SCOPE_SEPARATOR.equals(word);
      var nameSeq = MutableList.<String>create();
      for (int i = lastOfThePath; i >= 0; i--) {
        var w = tokens.get(i);
        if (w.type() == AyaPsiElementTypes.ID) {
          nameSeq.append(w.range().substring(line));
        } else if (w.type() != AyaPsiElementTypes.COLON2) {
          break;
        }
      }
      return new FixWord(nameSeq.reversed(), isFixed);
    }

    @SuppressWarnings("unchecked") @Override public void
    complete(LineReader reader, ParsedLine preline, List<Candidate> candidates) {
      if (!(preline instanceof ReplParser.ReplParsedLine<?> line)) return;
      var word = line.word();
      var fixed = fixWord(word, line.wordIndex(), (ImmutableSeq<FlexLexer.Token>) line.rawTokens(), line.line());
      var context = repl.replCompiler.getContext();
      // System.out.println(word + " ∈ " + line.words() + ", [idx, cursor, wordCursor]: "
      //   + line.wordIndex() + ", " + line.cursor() + ", " + line.wordCursor());
      context.giveMeHint(fixed.moduleName).forEach(candidate -> {
        if (fixed.fixed) {
          candidates.add(new Candidate(Constants.SCOPE_SEPARATOR + candidate));
        } else {
          if (candidate.startsWith(word)) candidates.add(new Candidate(candidate));
        }
      });
      // ice: maybe nobody needs this overly complicated user experience
      // // In case of a::b[cursor], we first assume `b` is the prefix of a symbol in the module `a`,
      // // but this might not always be the case, it's possible that `b` is already a module name,
      // // so we also complete `::c` for children in `b`.
      // if (!fixed.fixed && fixed.moduleName.isNotEmpty()) {
      //   ImmutableSeq<String> strings = context.giveMeHint(fixed.moduleName.appended(word));
      //   System.out.println(fixed.moduleName.appended(word) + ", result: " + strings);
      //   strings.forEach(candidate ->
      //     candidates.add(new Candidate(Constants.SCOPE_SEPARATOR + candidate)));
      // }
    }
  }

  class Code extends Context {
    public Code(@NotNull AyaRepl repl) { super(repl); }

    @Override public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
      KW.complete(reader, line, candidates);
      super.complete(reader, line, candidates);
    }
  }
}
