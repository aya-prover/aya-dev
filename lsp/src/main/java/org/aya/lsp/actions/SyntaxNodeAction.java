// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.lsp.actions;

import kala.tuple.Unit;
import org.aya.concrete.Decl;
import org.aya.concrete.Signatured;
import org.aya.concrete.visitor.StmtConsumer;
import org.aya.lsp.utils.XY;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public interface SyntaxNodeAction extends StmtConsumer<XY> {
  @Override default Unit visitCtor(@NotNull Decl.DataCtor ctor, XY xy) {
    return ok(ctor, xy) ? StmtConsumer.super.visitCtor(ctor, xy) : Unit.unit();
  }

  @Override default Unit visitField(@NotNull Decl.StructField field, XY xy) {
    return ok(field, xy) ? StmtConsumer.super.visitField(field, xy) : Unit.unit();
  }

  @Override default Unit visitFn(Decl.@NotNull FnDecl decl, XY xy) {
    return ok(decl, xy) ? StmtConsumer.super.visitFn(decl, xy) : Unit.unit();
  }

  @Override default Unit visitData(Decl.@NotNull DataDecl decl, XY xy) {
    return ok(decl, xy) ? StmtConsumer.super.visitData(decl, xy) : Unit.unit();
  }

  @Override default Unit visitStruct(Decl.@NotNull StructDecl decl, XY xy) {
    return ok(decl, xy) ? StmtConsumer.super.visitStruct(decl, xy) : Unit.unit();
  }

  @Override default Unit visitPrim(Decl.@NotNull PrimDecl decl, XY xy) {
    return ok(decl, xy) ? StmtConsumer.super.visitPrim(decl, xy) : Unit.unit();
  }

  private boolean ok(Signatured signatured, XY xy) {
    return xy.inside(signatured.entireSourcePos);
  }
}
