// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.concrete.remark;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableList;
import org.aya.concrete.GenericAyaParser;
import org.aya.concrete.stmt.Stmt;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
import org.aya.resolve.ResolveInfo;
import org.aya.resolve.context.Context;
import org.aya.tyck.order.TyckOrder;
import org.aya.util.error.SourcePos;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ice1000
 */
public final class Remark implements Stmt {
  public final @Nullable Literate literate;
  public final @NotNull String raw;
  public final @NotNull SourcePos sourcePos;
  public @Nullable Context ctx = null;

  private Remark(@Nullable Literate literate, @NotNull String raw, @NotNull SourcePos sourcePos) {
    this.literate = literate;
    this.raw = raw;
    this.sourcePos = sourcePos;
  }

  public static @NotNull Remark make(@NotNull String raw, @NotNull SourcePos pos, @NotNull GenericAyaParser ayaParser) {
    var mdParser = Parser.builder().customDelimiterProcessor(CodeAttrProcessor.INSTANCE).build();
    var ast = mdParser.parse(raw);
    return new Remark(mapAST(ast, pos, ayaParser), raw, pos);
  }

  private static @NotNull ImmutableSeq<Literate> mapChildren(
    @NotNull Node parent, @NotNull SourcePos pos,
    @NotNull GenericAyaParser producer
  ) {
    Node next;
    var children = MutableList.<Literate>create();
    for (var node = parent.getFirstChild(); node != null; node = next) {
      if (children.isNotEmpty() && node instanceof Paragraph) {
        children.append(new Literate.Raw(Doc.line()));
      }
      next = node.getNext();
      children.append(mapAST(node, pos, producer));
    }
    return children.toImmutableSeq();
  }

  private static @NotNull Literate mapAST(
    @NotNull Node node, @NotNull SourcePos pos,
    @NotNull GenericAyaParser producer
  ) {
    if (node instanceof Code code) {
      return CodeOptions.analyze(code, producer.expr(code.getLiteral(), pos));
    } else if (node instanceof Text text) {
      return new Literate.Raw(Doc.plain(text.getLiteral()));
    } else if (node instanceof Emphasis emphasis) {
      return new Literate.Many(Style.italic(), mapChildren(emphasis, pos, producer));
    } else if (node instanceof HardLineBreak || node instanceof SoftLineBreak) {
      return new Literate.Raw(Doc.line());
    } else if (node instanceof StrongEmphasis emphasis) {
      return new Literate.Many(Style.bold(), mapChildren(emphasis, pos, producer));
    } else if (node instanceof Paragraph) {
      return new Literate.Many(null, mapChildren(node, pos, producer));
    } else if (node instanceof Document) {
      var children = mapChildren(node, pos, producer);
      if (children.sizeEquals(1)) return children.first();
      else return new Literate.Many(null, children);
    } else {
      producer.reporter().report(new UnsupportedMarkdown(pos, node.getClass().getSimpleName()));
      return new Literate.Unsupported(mapChildren(node, pos, producer));
    }
  }

  @Override public @NotNull Accessibility accessibility() {
    return Accessibility.Private;
  }

  public @NotNull SourcePos sourcePos() {
    return sourcePos;
  }

  public @NotNull ImmutableSeq<TyckOrder> doResolve(@NotNull ResolveInfo info) {
    if (literate == null) return ImmutableSeq.empty();
    assert ctx != null : "Be sure to call the shallow resolver before resolving";
    return literate.resolve(info, ctx);
  }

  /** It's always downstream (cannot be imported), so always need to be checked. */
  @Override public boolean needTyck(@NotNull ImmutableSeq<String> currentMod) {
    return true;
  }
}
