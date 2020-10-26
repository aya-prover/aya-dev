// Copyright (c) 2020-2020 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the Apache-2.0 license that can be found in the LICENSE file.
package org.mzi.concrete.parse;

import org.jetbrains.annotations.NotNull;
import org.mzi.generic.Assoc;
import org.mzi.parser.MziBaseVisitor;
import org.mzi.parser.MziParser;

/**
 * @author ice1000
 */
public class MziProducer extends MziBaseVisitor<Object> {
  @Override public @NotNull Assoc visitAssoc(MziParser.AssocContext ctx) {
    if (ctx.FIX() != null) return Assoc.Fix;
    else if (ctx.FIXL() != null) return Assoc.FixL;
    else if (ctx.FIXR() != null) return Assoc.FixR;
    else if (ctx.INFIX() != null) return Assoc.Infix;
    else if (ctx.INFIXL() != null) return Assoc.InfixL;
    else if (ctx.INFIXR() != null) return Assoc.InfixR;
    else if (ctx.TWIN() != null) return Assoc.Twin;
    else throw new IllegalArgumentException(ctx.getClass() + ": " + ctx.getText());
  }
}
