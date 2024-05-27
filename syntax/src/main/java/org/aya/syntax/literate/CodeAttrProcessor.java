// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.syntax.literate;

import com.intellij.openapi.util.text.StringUtil;
import kala.collection.mutable.MutableList;
import org.aya.prettier.AyaPrettierOptions;
import org.aya.syntax.literate.CodeOptions.NormalizeMode;
import org.commonmark.node.*;
import org.commonmark.parser.delimiter.DelimiterProcessor;
import org.commonmark.parser.delimiter.DelimiterRun;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * <a href="https://github.com/commonmark/commonmark-java/blob/main/commonmark-ext-image-attributes/src/main/java/org/commonmark/ext/image/attributes/internal/ImageAttributesDelimiterProcessor.java">...</a>
 */
public enum CodeAttrProcessor implements DelimiterProcessor {
  INSTANCE;

  private static final @NotNull Pattern DELIM = Pattern.compile("[\\s,;]+");
  private static final @NotNull Pattern EQ = Pattern.compile("=");

  public static class Attr extends CustomNode implements Delimited {
    public final @NotNull CodeOptions options;
    public Attr(@NotNull CodeOptions options) { this.options = options; }
    @Override public String getOpeningDelimiter() { return "{"; }
    @Override public String getClosingDelimiter() { return "}"; }
  }
  @Override public char getOpeningCharacter() { return '{'; }
  @Override public char getClosingCharacter() { return '}'; }
  @Override public int getMinLength() { return 1; }

  @Override public int process(DelimiterRun openingRun, DelimiterRun closingRun) {
    if (openingRun.length() != 1) {
      return 0;
    }

    var opener = openingRun.getOpener();
    if (!(opener.getPrevious() instanceof Code code)) return 0;

    var toUnlink = MutableList.<Node>create();
    var content = new StringBuilder();

    for (var node : Nodes.between(opener, closingRun.getCloser())) {
      // Only Text nodes can be used for attributes
      if (node instanceof Text text) {
        content.append(text.getLiteral());
        toUnlink.append(text);
      } else {
        // This node type is not supported, so stop here (no need to check any further ones).
        return 0;
      }
    }

    var dist = new AyaPrettierOptions();
    var mode = NormalizeMode.NULL;
    var show = CodeOptions.ShowCode.Core;
    for (var s : DELIM.split(content.toString())) {
      if (s.isBlank()) continue;
      var attribute = EQ.split(s, 2);
      if (attribute.length > 1) {
        var key = attribute[0];
        var val = attribute[1];
        if ("mode".equalsIgnoreCase(key)) {
          mode = cbt(val, NormalizeMode.values(), NormalizeMode.NULL);
          continue;
        }
        if ("show".equalsIgnoreCase(key)) {
          show = cbt(val, CodeOptions.ShowCode.values(), CodeOptions.ShowCode.Core);
          continue;
        }
        var cbt = cbt(key, AyaPrettierOptions.Key.values(), null);
        if (cbt != null) {
          var isTrue = val.equalsIgnoreCase("true") || val.equalsIgnoreCase("yes");
          dist.map.put(cbt, isTrue);
          continue;
        }
      }
      // This attribute is not supported, so stop here (no need to check any further ones).
      return 0;
    }

    // Unlink the tmp nodes
    for (var node : toUnlink) node.unlink();

    if (!dist.map.isEmpty()) {
      var imageAttributes = new Attr(new CodeOptions(mode, dist, show));

      // The new node is added as a child of the image node to which the attributes apply.
      code.appendChild(imageAttributes);
    }
    return 1;
  }

  private <E extends Enum<E>> E cbt(@NotNull String key, E[] values, E otherwise) {
    for (var val : values)
      if (StringUtil.containsIgnoreCase(val.name(), key)) return val;
    return otherwise;
  }
}
