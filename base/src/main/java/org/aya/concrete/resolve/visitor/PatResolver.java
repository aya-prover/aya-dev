// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
package org.aya.concrete.resolve.visitor;

import org.aya.api.ref.DefVar;
import org.aya.concrete.Decl;
import org.aya.concrete.Pattern;
import org.aya.concrete.resolve.context.Context;
import org.aya.concrete.resolve.error.NotCtorError;
import org.aya.concrete.resolve.error.UnexpectedParamError;
import org.aya.core.def.DataDef;
import org.aya.generic.Atom;
import org.glavo.kala.collection.immutable.ImmutableSeq;
import org.glavo.kala.collection.mutable.Buffer;
import org.glavo.kala.tuple.Tuple;
import org.glavo.kala.tuple.Tuple2;
import org.glavo.kala.value.Ref;
import org.jetbrains.annotations.NotNull;

/**
 * @author ice1000
 */
public final class PatResolver implements
  Pattern.Clause.Visitor<Context, Pattern.Clause>,
  Pattern.Visitor<Context, Tuple2<Context, Pattern>> {
  public static final @NotNull PatResolver INSTANCE = new PatResolver();

  private PatResolver() {
  }

  @Override public Pattern.Clause visitMatch(Pattern.Clause.@NotNull Match match, Context context) {
    var ctx = new Ref<>(context);
    var pats = match.patterns().stream().sequential().map(pat -> {
      var res = pat.accept(this, ctx.value);
      ctx.value = res._1;
      return res._2;
    }).collect(Buffer.factory());
    return new Pattern.Clause.Match(pats, match.expr().resolve(ctx.value));
  }

  @Override public Pattern.Clause visitAbsurd(Pattern.Clause.@NotNull Absurd absurd, Context context) {
    return null;
  }

  @Override public Tuple2<Context, Pattern> visitAtomic(Pattern.@NotNull Atomic atomic, Context context) {
    throw new IllegalArgumentException("before resolving, we can never have resolved patterns");
  }

  @Override public Tuple2<Context, Pattern> visitCtor(Pattern.@NotNull Ctor ctor, Context context) {
    throw new IllegalArgumentException("before resolving, we can never have resolved patterns");
  }

  @Override public Tuple2<Context, Pattern> visitUnresolved(Pattern.@NotNull Unresolved pat, Context context) {
    var first = ((Pattern.Atomic) pat.fields().first()); // ensured by AyaProducer
    var rest = pat.fields().drop(1);

    var atom = first.atom();
    if (atom instanceof Atom.Bind<Pattern> bind) {
      // the bind id may refer to a data ctor, so let's see if we have the ctor in current scope
      // TODO[kiva]: support qualified access (#180)
      var what = context.getUnqualifiedMaybe(bind.bind().name(), bind.sourcePos());

      if (what instanceof DefVar<?, ?> defVar) {
        if (defVar.concrete instanceof Decl.DataCtor) {
          // this case is totally safe
          //noinspection unchecked
          return Tuple.of(
            context,
            new Pattern.Ctor(pat.sourcePos(), (DefVar<DataDef.Ctor, Decl.DataCtor>) defVar, rest, pat.as())
          );
        }

        if (!rest.isEmpty()) {
          // the bind id refer to a fn-like thing (not a data ctor) and have params
          context.reportAndThrow(new NotCtorError(bind.bind().name(), pat.sourcePos()));
        }
      }
    }

    // this must be a bind id so it cannot have params
    requiresNoParam(atom, context, rest);
    return Tuple.of(
      context,
      new Pattern.Atomic(pat.sourcePos(), atom, pat.as())
    );
  }

  private void requiresNoParam(@NotNull Atom<Pattern> atom, @NotNull Context context, @NotNull ImmutableSeq<Pattern> seq) {
    if (!seq.isEmpty()) {
      // TODO[kiva]: pretty-print atom
      context.reportAndThrow(new UnexpectedParamError(atom.toString(), atom.sourcePos()));
    }
  }
}
