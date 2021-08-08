// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.remark;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import org.aya.api.error.SourcePos;
import org.aya.api.util.NormalizeMode;
import org.aya.concrete.desugar.BinOpSet;
import org.aya.concrete.parse.AyaParsing;
import org.aya.concrete.parse.AyaProducer;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.stmt.Stmt;
import org.aya.pretty.doc.Doc;
import org.aya.pretty.doc.Style;
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

  public static @NotNull Remark make(@NotNull String raw, @NotNull SourcePos pos, @NotNull AyaProducer producer) {
    var parser = Parser.builder().build();
    var ast = parser.parse(raw);
    return new Remark(mapAST(ast, pos, producer), raw, pos);
  }

  private static @NotNull ImmutableSeq<Literate> mapChildren(
    @NotNull Node parent, @NotNull SourcePos pos,
    @NotNull AyaProducer producer
  ) {
    Node next;
    var children = Buffer.<Literate>create();
    for (var node = parent.getFirstChild(); node != null; node = next) {
      if (children.isNotEmpty() && node instanceof Paragraph) {
        children.append(new Literate.Raw(Doc.line()));
      }
      next = node.getNext();
      children.append(mapAST(node, pos, producer));
    }
    return children.toImmutableSeq();
  }

  private static @Nullable Literate mapAST(
    @NotNull Node node, @NotNull SourcePos pos,
    @NotNull AyaProducer producer
  ) {
    if (node instanceof Code code) {
      var text = code.getLiteral();
      Literate.ShowCode show;
      if (text.startsWith("ty:") || text.startsWith("TY:")) {
        show = Literate.ShowCode.Type;
        text = text.substring(3);
      } else if (text.startsWith("core:") || text.startsWith("CORE:")) {
        show = Literate.ShowCode.Core;
        text = text.substring(5);
      } else show = Literate.ShowCode.Concrete;
      NormalizeMode mode = null;
      if (show != Literate.ShowCode.Concrete) for (var value : NormalizeMode.values()) {
        var prefix = value + ":";
        if (text.startsWith(prefix)) {
          mode = value;
          text = text.substring(prefix.length());
          break;
        }
      }
      var expr = producer.visitExpr(AyaParsing.parser(text).expr());
      return new Literate.Code(expr, show, mode);
    } else if (node instanceof Text text) {
      return new Literate.Raw(Doc.plain(text.getLiteral()));
    } else if (node instanceof Emphasis emphasis) {
      return new Literate.Many(Style.italic(), mapChildren(emphasis, pos, producer));
    } else if (node instanceof HardLineBreak) {
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
      producer.reporter.report(new UnsupportedMarkdown(pos, node.getClass().getSimpleName()));
      return null;
    }
  }

  @Override public @NotNull Accessibility accessibility() {
    return Accessibility.Private;
  }

  @Override public <P, R> R doAccept(@NotNull Visitor<P, R> visitor, P p) {
    return visitor.visitRemark(this, p);
  }

  public @NotNull SourcePos sourcePos() {
    return sourcePos;
  }

  public void doResolve(@NotNull BinOpSet binOpSet) {
    if (literate == null) return;
    assert ctx != null : "Be sure to call the shallow resolver before resolving";
    literate.resolve(binOpSet, ctx);
  }
}
