// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.remark;

import kala.collection.mutable.DynamicSeq;
import org.aya.api.distill.DistillerOptions;
import org.aya.api.util.NormalizeMode;
import org.commonmark.node.*;
import org.commonmark.parser.delimiter.DelimiterProcessor;
import org.commonmark.parser.delimiter.DelimiterRun;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * https://github.com/commonmark/commonmark-java/blob/main/commonmark-ext-image-attributes/src/main/java/org/commonmark/ext/image/attributes/internal/ImageAttributesDelimiterProcessor.java
 */
public class CodeAttrProcessor implements DelimiterProcessor {
  public static class Attr extends CustomNode implements Delimited {
    public final @NotNull CodeOptions options;

    public Attr(@NotNull CodeOptions options) {
      this.options = options;
    }

    @Override public String getOpeningDelimiter() {
      return "{";
    }

    @Override public String getClosingDelimiter() {
      return "}";
    }
  }

  @Override public char getOpeningCharacter() {
    return '{';
  }

  @Override public char getClosingCharacter() {
    return '}';
  }

  @Override public int getMinLength() {
    return 1;
  }

  @Override public int process(DelimiterRun openingRun, DelimiterRun closingRun) {
    if (openingRun.length() != 1) {
      return 0;
    }

    // Check if the attributes can be applied - if the previous node is an Image, and if all the attributes are in
    // the set of SUPPORTED_ATTRIBUTES
    var opener = openingRun.getOpener();
    if (!(opener.getPrevious() instanceof Code code)) {
      return 0;
    }

    var toUnlink = DynamicSeq.<Node>create();
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

    var dist = new DistillerOptions();
    var mode = NormalizeMode.NULL;
    var show = CodeOptions.ShowCode.Core;
    var attributes = content.toString();
    for (var s : attributes.split("[\\s,;]+")) {
      var attribute = s.split("=", 2);
      if (attribute.length > 1) {
        var key = attribute[0].toLowerCase(Locale.ROOT);
        var isTrue = attribute[1].equalsIgnoreCase("true")
          || attribute[1].equalsIgnoreCase("yes");
        var cbt = cbt(key, DistillerOptions.Key.values(), null);
        if (cbt != null) {
          dist.map.put(cbt, isTrue);
          continue;
        }
        if (key.equals("mode")) {
          mode = cbt(attribute[1].toUpperCase(Locale.ROOT), NormalizeMode.values(), NormalizeMode.NULL);
          continue;
        }
        if (key.equals("show")) {
          show = cbt(attribute[1].toUpperCase(Locale.ROOT), CodeOptions.ShowCode.values(), CodeOptions.ShowCode.Core);
          continue;
        }
      }
      // This attribute is not supported, so stop here (no need to check any further ones).
      return 0;
    }

    // Unlink the tmp nodes
    for (var node : toUnlink) node.unlink();

    if (dist.map.size() > 0) {
      var imageAttributes = new Attr(new CodeOptions(mode, dist, show));

      // The new node is added as a child of the image node to which the attributes apply.
      code.appendChild(imageAttributes);
    }
    return 1;
  }

  private <E extends Enum<E>> E cbt(@NotNull String key, E[] values, E otherwise) {
    for (var val : values)
      if (val.name().toLowerCase(Locale.ROOT).contains(key)) return val;
    return otherwise;
  }
}
