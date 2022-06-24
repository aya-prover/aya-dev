// Copyright (c) 2020-2022 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.core.visitor;

import kala.collection.immutable.ImmutableMap;
import kala.collection.immutable.ImmutableSeq;
import kala.tuple.Unit;
import org.aya.concrete.stmt.TopTeleDecl;
import org.aya.core.Meta;
import org.aya.core.def.*;
import org.aya.core.pat.Pat;
import org.aya.core.repr.AyaShape;
import org.aya.generic.AyaDocile;
import org.aya.ref.DefVar;
import org.aya.ref.LocalVar;

/**
 * A `TerminalTraveller` always yields a dummy value as the result of traversal.
 * This interface is more useful when used as an escape hatch:
 * The implementing class can hold and interact with arbitrary states during traversal.
 *
 * @author wsx
 */
public interface TerminalTraveller extends Traveller<Unit> {
  default Unit ref(LocalVar var) {
    return Unit.unit();
  }
  default Unit lambda(Param<Unit> param, Unit body) {
    return Unit.unit();
  }
  default Unit pi(Param<Unit> param, Unit body) {
    return Unit.unit();
  }
  default Unit sigma(ImmutableSeq<Param<Unit>> params) {
    return Unit.unit();
  }
  default Unit univ(int lift) {
    return Unit.unit();
  }
  default Unit app(Unit of, Arg<Unit> arg) {
    return Unit.unit();
  }
  default Unit fn(DefVar<FnDef, TopTeleDecl.FnDecl> ref, int lift, ImmutableSeq<Arg<Unit>> args) {
    return Unit.unit();
  }
  default Unit data(DefVar<DataDef, TopTeleDecl.DataDecl> ref, int lift, ImmutableSeq<Arg<Unit>> args) {
    return Unit.unit();
  }
  default Unit con(Unit data, DefVar<CtorDef, TopTeleDecl.DataCtor> ref, ImmutableSeq<Arg<Unit>> args) {
    return Unit.unit();
  }
  default Unit struct(DefVar<StructDef, TopTeleDecl.StructDecl> ref, int lift, ImmutableSeq<Arg<Unit>> args) {
    return Unit.unit();
  }
  default Unit prim(DefVar<PrimDef, TopTeleDecl.PrimDecl> ref, PrimDef.ID id, int lift, ImmutableSeq<Arg<Unit>> args) {
    return Unit.unit();
  }
  default Unit tuple(ImmutableSeq<Unit> items) {
    return Unit.unit();
  }
  default Unit nevv(Unit struct, ImmutableMap<DefVar<FieldDef, TopTeleDecl.StructField>, Unit> fields) {
    return Unit.unit();
  }
  default Unit proj(Unit of, int ix) {
    return Unit.unit();
  }
  default Unit access(Unit of, DefVar<FieldDef, TopTeleDecl.StructField> ref, ImmutableSeq<Arg<Unit>> structArgs, ImmutableSeq<Arg<Unit>> fieldArgs) {
    return Unit.unit();
  }
  default Unit hole(Meta ref, int lift, ImmutableSeq<Arg<Unit>> contextArgs, ImmutableSeq<Arg<Unit>> args) {
    return Unit.unit();
  }
  default Unit field(DefVar<FieldDef, TopTeleDecl.StructField> ref, int lift) {
    return Unit.unit();
  }
  default Unit error(AyaDocile description, boolean isReallyError) {
    return Unit.unit();
  }
  default Unit metaPat(Pat.Meta ref, int lift) {
    return Unit.unit();
  }
  default Unit interval() {
    return Unit.unit();
  }
  default Unit end(boolean side) {
    return Unit.unit();
  }
  default Unit str(String s) {
    return Unit.unit();
  }
  default Unit shaped(int repr, AyaShape shape, Unit type) {
    return Unit.unit();
  }
}
