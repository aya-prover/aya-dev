// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.literate;

import org.commonmark.node.Block;
import org.commonmark.node.CustomBlock;
import org.commonmark.parser.SourceLine;
import org.commonmark.parser.block.*;
import org.commonmark.text.Characters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * @see org.commonmark.node.FencedCodeBlock
 */
public class FencedBlock extends CustomBlock {
  public char fenceChar;
  public int fenceLength;
  public int fenceIndent;
  /** @see org.commonmark.internal.util.Parsing.CODE_BLOCK_INDENT */
  public static final int CODE_BLOCK_INDENT = 4;

  public String literal;

  /**
   * @see org.commonmark.internal.FencedCodeBlockParser
   */
  public static class Parser<T extends FencedBlock> extends AbstractBlockParser {
    private final @NotNull T block;

    private @Nullable String firstLine;
    private final @NotNull StringBuilder otherLines = new StringBuilder();

    public Parser(@NotNull T block) {
      this.block = block;
    }

    @Override public Block getBlock() {
      return block;
    }

    @Override public BlockContinue tryContinue(ParserState state) {
      int nextNonSpace = state.getNextNonSpaceIndex();
      int newIndex = state.getIndex();
      var line = state.getLine().getContent();
      if (state.getIndent() < CODE_BLOCK_INDENT && nextNonSpace < line.length() && line.charAt(nextNonSpace) == block.fenceChar && isClosing(line, nextNonSpace)) {
        // closing fence - we're at the end of line, so we can finalize now
        return BlockContinue.finished();
      } else {
        // skip optional spaces of fence indent
        int i = block.fenceIndent;
        int length = line.length();
        while (i > 0 && newIndex < length && line.charAt(newIndex) == ' ') {
          newIndex++;
          i--;
        }
      }
      return BlockContinue.atIndex(newIndex);
    }

    @Override public void addLine(SourceLine line) {
      if (firstLine == null) {
        firstLine = line.getContent().toString();
      } else {
        otherLines.append(line.getContent());
        otherLines.append('\n');
      }
    }

    @Override public void closeBlock() {
      block.literal = otherLines.toString();
    }

    // spec: The content of the code block consists of all subsequent lines, until a closing code fence of the same type
    // as the code block began with (backticks or tildes), and with at least as many backticks or tildes as the opening
    // code fence.
    private boolean isClosing(CharSequence line, int index) {
      char fenceChar = block.fenceChar;
      int fenceLength = block.fenceLength;
      int fences = Characters.skip(fenceChar, line, index, line.length()) - index;
      if (fences < fenceLength) {
        return false;
      }
      // spec: The closing code fence [...] may be followed only by spaces, which are ignored.
      int after = Characters.skipSpaceTab(line, index + fences, line.length());
      return after == line.length();
    }
  }

  public static class Factory<T extends FencedBlock> extends AbstractBlockParserFactory {
    public final char fenceChar;
    public final int minFenceLength;
    public final @NotNull Supplier<T> creator;

    public Factory(@NotNull Supplier<T> creator, char fenceChar, int minFenceLength) {
      this.creator = creator;
      this.fenceChar = fenceChar;
      this.minFenceLength = minFenceLength;
    }

    @Override public @Nullable BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser) {
      int indent = state.getIndent();
      if (indent >= CODE_BLOCK_INDENT) {
        return BlockStart.none();
      }

      int nextNonSpace = state.getNextNonSpaceIndex();
      var blockParser = checkOpener(state.getLine().getContent(), nextNonSpace, indent);
      if (blockParser != null) {
        return BlockStart.of(blockParser).atIndex(nextNonSpace + blockParser.block.fenceLength);
      } else {
        return BlockStart.none();
      }
    }

    private @Nullable FencedBlock.Parser<T> checkOpener(CharSequence line, int index, int indent) {
      int hit = 0;
      for (int i = index; i < line.length(); i++) {
        if (line.charAt(i) == fenceChar) hit++;
        else break;
      }
      if (hit < minFenceLength) return null;
      var block = creator.get();
      block.fenceIndent = indent;
      block.fenceChar = fenceChar;
      block.fenceLength = hit;
      return new Parser<>(block);
    }
  }
}
