// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import org.aya.concrete.stmt.TopTeleDecl;
import org.aya.core.Meta;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.repr.AyaShape;
import org.aya.generic.AyaDocile;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;

/**
 * A convenient interface when the term structure can be mapped nicely into a monoidal structure.
 * One only need to provide the monoidal operation and unit, as well as any overrides for term variants.
 *
 * @author wsx
 */
public interface MonoidalTraveller<R> extends Traveller<R> {
  R e();
  R op(R a, R b);

  default R ref(LocalVar var) {
    return e();
  }
  default R lambda(Param<R> param, R body) {
    return op(param.type(), body);
  }
  default R pi(Param<R> param, R body) {
    return op(param.type(), body);
  }
  default R sigma(ImmutableSeq<Param<R>> params) {
    return params.view().map(Param::type).fold(e(), this::op);
  }
  default R univ(int lift) {
    return e();
  }
  default R app(R of, Arg<R> arg) {
    return op(of, arg.term());
  }
  default R fn(DefVar<FnDef, TopTeleDecl.FnDecl> ref, int lift, ImmutableSeq<Arg<R>> args) {
    return args.view().map(Arg::term).fold(e(), this::op);
  }
  default R data(DefVar<DataDef, TopTeleDecl.DataDecl> ref, int lift, ImmutableSeq<Arg<R>> args) {
    return args.view().map(Arg::term).fold(e(), this::op);
  }
  default R con(R data, DefVar<CtorDef, TopTeleDecl.DataCtor> ref, ImmutableSeq<Arg<R>> args) {
    return op(data, args.view().map(Arg::term).fold(e(), this::op));
  }
  default R struct(DefVar<StructDef, TopTeleDecl.StructDecl> ref, int lift, ImmutableSeq<Arg<R>> args) {
    return args.view().map(Arg::term).fold(e(), this::op);
  }
  default R prim(DefVar<PrimDef, TopTeleDecl.PrimDecl> ref, PrimDef.ID id, int lift, ImmutableSeq<Arg<R>> args) {
    return args.view().map(Arg::term).fold(e(), this::op);
  }
  default R tuple(ImmutableSeq<R> items) {
    return items.fold(e(), this::op);
  }
  default R nevv(R struct, ImmutableMap<DefVar<FieldDef, TopTeleDecl.StructField>, R> fields) {
    return op(struct, fields.valuesView().fold(e(), this::op));
  }
  default R proj(R of, int ix) {
    return of;
  }
  default R access(R of, DefVar<FieldDef, TopTeleDecl.StructField> ref, ImmutableSeq<Arg<R>> structArgs, ImmutableSeq<Arg<R>> fieldArgs) {
    return op(
      op(of, structArgs.view().map(Arg::term).fold(e(), this::op)),
      fieldArgs.view().map(Arg::term).fold(e(), this::op)
    );
  }
  default R hole(Meta ref, int lift, ImmutableSeq<Arg<R>> contextArgs, ImmutableSeq<Arg<R>> args) {
    return op(
      contextArgs.view().map(Arg::term).fold(e(), this::op),
      args.view().map(Arg::term).fold(e(), this::op)
    );
  }
  default R field(DefVar<FieldDef, TopTeleDecl.StructField> ref, int lift) {
    return e();
  }
  default R error(AyaDocile description, boolean isReallyError) {
    return e();
  }
  default R metaPat(Pat.Meta ref, int lift) {
    return e();
  }
  default R interval() {
    return e();
  }
  default R end(boolean side) {
    return e();
  }
  default R str(String s) {
    return e();
  }
  default R shaped(int repr, AyaShape shape, R type) {
    return type;
  }
}
