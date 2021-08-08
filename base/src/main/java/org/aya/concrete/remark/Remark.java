// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.remark;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.Buffer;
import kala.value.Ref;
import org.aya.api.error.SourcePos;
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
    return new Remark(mapAST(ast, producer), raw, pos);
  }

  public static @NotNull ImmutableSeq<Literate> mapChildren(@NotNull Node parent, @NotNull AyaProducer producer) {
    Node next;
    var children = Buffer.<Literate>create();
    for(Node node = parent.getFirstChild(); node != null; node = next) {
      next = node.getNext();
      children.append(mapAST(node, producer));
    }
    return children.toImmutableSeq();
  }

  public static @Nullable Literate mapAST(@NotNull Node node, @NotNull AyaProducer producer) {
    if (node instanceof Code code) {
      var expr = producer.visitExpr(AyaParsing.parser(code.getLiteral()).expr());
      return new Literate.Code(new Ref<>(expr), new Literate.CodeCmd(false, null));
    } else if (node instanceof Text text) {
      return new Literate.Raw(Doc.plain(text.getLiteral()));
    } else if (node instanceof Emphasis emphasis) {
      return new Literate.Styled(Style.italic(), mapChildren(emphasis, producer));
    } else if (node instanceof HardLineBreak) {
      return new Literate.Raw(Doc.line());
    } else if (node instanceof StrongEmphasis emphasis) {
      return new Literate.Styled(Style.bold(), mapChildren(emphasis, producer));
    } else if (node instanceof Paragraph paragraph) {
      return new Literate.Par(mapChildren(paragraph, producer));
    } else {
      // TODO: producer.reporter().report();
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
