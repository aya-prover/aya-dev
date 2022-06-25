// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.SeqLike;
import org.aya.core.term.*;

/**
 * A convenient interface when the term structure can be mapped nicely into a monoidal structure.
 * One only need to provide the monoidal operation and unit, as well as any overrides for term variants.
 *
 * @author wsx
 */
public interface MonoidalFolder<R> extends Folder<R> {
  R e();
  R op(R a, R b);
  default R ops(SeqLike<R> rs) {
    return rs.fold(e(), this::op);
  }

  default R fold(Tm<R> tm) {
    return switch (tm) {
      case Tm.Pi<R> pi -> op(pi.param().type(), pi.body());
      case Tm.Sigma<R> sigma -> ops(sigma.params().view().map(Tm.Param::type));
      case Tm.Lambda<R> lambda -> op(lambda.param().type(), lambda.body());
      case Tm.Tuple<R> tuple -> ops(tuple.items());
      case Tm.New<R> nevv -> op(nevv.struct(), ops(nevv.fields().valuesView().toImmutableSeq()));
      case Tm.App<R> app -> op(app.of(), app.arg().term());
      case Tm.Proj<R> proj -> proj.of();
      case Tm.Struct<R> struct -> ops(struct.args().view().map(Tm.Arg::term));
      case Tm.Data<R> data -> ops(data.args().view().map(Tm.Arg::term));
      case Tm.Con<R> con -> ops(con.args().view().map(Tm.Arg::term));
      case Tm.Fn<R> fn -> ops(fn.args().view().map(Tm.Arg::term));
      case Tm.Access<R> access ->
        ops(access.structArgs().view().map(Tm.Arg::term).concat(access.fieldArgs().view().map(Tm.Arg::term)).prepended(access.of()));
      case Tm.Prim<R> prim -> ops(prim.args().view().map(Tm.Arg::term));
      case Tm.Hole<R> hole ->
        ops(hole.contextArgs().view().map(Tm.Arg::term).concat(hole.args().view().map(Tm.Arg::term)));
      case Tm.ShapedInt<R> shaped -> shaped.type();
      case Tm<R> ignored -> e();
    };
  }
}
