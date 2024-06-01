// Copyright (c) 2020-2024 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler;

import com.intellij.openapi.util.text.StringUtil;
import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableMap;
import org.aya.generic.NameGenerator;
import org.aya.generic.stmt.Shaped;
import org.aya.generic.term.SortKind;
import org.aya.syntax.compile.JitFn;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.*;
import org.aya.syntax.core.term.marker.TyckInternal;
import org.aya.syntax.core.term.repr.*;
import org.aya.syntax.core.term.xtt.*;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.error.Panic;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

import static org.aya.compiler.AyaSerializer.*;
import static org.aya.compiler.NameSerializer.getClassReference;

/**
 * Build the "constructor form" of {@link Term}, but in Java.
 */
public class TermExprializer extends AbstractExprializer<Term> {
  public static final String CLASS_LAMTERM = ExprializeUtils.getJavaReference(LamTerm.class);
  public static final String CLASS_JITLAMTERM = ExprializeUtils.getJavaReference(Closure.Jit.class);
  public static final String CLASS_APPTERM = ExprializeUtils.getJavaReference(AppTerm.class);
  public static final String CLASS_SORTKIND = ExprializeUtils.getJavaReference(SortKind.class);
  public static final String CLASS_INTOPS = ExprializeUtils.getJavaReference(IntegerOps.class);
  public static final String CLASS_LISTOPS = ExprializeUtils.getJavaReference(ListOps.class);
  public static final String CLASS_INTEGER = ExprializeUtils.getJavaReference(IntegerTerm.class);
  public static final String CLASS_LIST = ExprializeUtils.getJavaReference(ListTerm.class);
  public static final String CLASS_STRING = ExprializeUtils.getJavaReference(StringTerm.class);
  public static final String CLASS_LOCALTERM = ExprializeUtils.getJavaReference(LocalTerm.class);
  public static final String CLASS_INT_CONRULE = ExprializeUtils.makeSub(CLASS_INTOPS, ExprializeUtils.getJavaReference(IntegerOps.ConRule.class));
  public static final String CLASS_INT_FNRULE = ExprializeUtils.makeSub(CLASS_INTOPS, ExprializeUtils.getJavaReference(IntegerOps.FnRule.class));
  public static final String CLASS_LIST_CONRULE = ExprializeUtils.makeSub(CLASS_LISTOPS, ExprializeUtils.getJavaReference(ListOps.ConRule.class));
  public static final String CLASS_FNRULE_KIND = ExprializeUtils.makeSub(CLASS_INT_FNRULE, ExprializeUtils.getJavaReference(IntegerOps.FnRule.Kind.class));
  public static final String CLASS_RULEREDUCER = ExprializeUtils.getJavaReference(RuleReducer.class);
  public static final String CLASS_RULE_CON = ExprializeUtils.makeSub(CLASS_RULEREDUCER, ExprializeUtils.getJavaReference(RuleReducer.Con.class));
  public static final String CLASS_RULE_FN = ExprializeUtils.makeSub(CLASS_RULEREDUCER, ExprializeUtils.getJavaReference(RuleReducer.Fn.class));

  /**
   * Terms that should be instantiated
   */
  private final @NotNull ImmutableSeq<String> instantiates;
  private final @NotNull MutableMap<LocalVar, String> binds;

  /**
   * Whether allow LocalTerm, false in default (in order to report unexpected LocalTerm)
   */
  private final boolean allowLocalTerm;

  public TermExprializer(@NotNull NameGenerator nameGen, @NotNull ImmutableSeq<String> instantiates) {
    this(nameGen, instantiates, false);
  }

  public TermExprializer(@NotNull NameGenerator nameGen, @NotNull ImmutableSeq<String> instantiates, boolean allowLocalTer) {
    super(nameGen);
    this.instantiates = instantiates;
    this.allowLocalTerm = allowLocalTer;
    this.binds = MutableMap.create();
  }

  private @NotNull String serializeApplicable(@NotNull Shaped.Applicable<?> applicable) {
    return switch (applicable) {
      case IntegerOps.ConRule conRule ->
        ExprializeUtils.makeNew(CLASS_INT_CONRULE, ExprializeUtils.getInstance(NameSerializer.getClassReference(conRule.ref())),
          doSerialize(conRule.zero())
        );
      case IntegerOps.FnRule fnRule -> ExprializeUtils.makeNew(CLASS_INT_FNRULE,
        ExprializeUtils.getInstance(NameSerializer.getClassReference(fnRule.ref())),
        ExprializeUtils.makeSub(CLASS_FNRULE_KIND, fnRule.kind().toString())
      );
      case ListOps.ConRule conRule -> ExprializeUtils.makeNew(CLASS_LIST_CONRULE,
        ExprializeUtils.getInstance(NameSerializer.getClassReference(conRule.ref())),
        doSerialize(conRule.empty())
      );
      default -> Panic.unreachable();
    };
  }

  /**
   * This code requires that {@link FnCall}, {@link RuleReducer.Fn} and {@link RuleReducer.Con}
   * {@code ulift} is the second parameter, {@code args.get(i)} is the {@code i + 3}th parameter
   *
   * @param fixed whether {@param reducible} has fixed `invoke`
   */
  private @NotNull String buildReducibleCall(
    @NotNull String reducible,
    @NotNull String callName,
    int ulift,
    @NotNull ImmutableSeq<ImmutableSeq<Term>> args,
    boolean fixed
  ) {
    var seredArgs = args.map(x -> x.map(this::doSerialize));
    var seredSeq = seredArgs.map(x -> ExprializeUtils.makeImmutableSeq(CLASS_TERM, x));
    var flatArgs = seredArgs.flatMap(x -> x);

    var callArgs = new String[seredSeq.size() + 2];
    callArgs[0] = reducible;
    callArgs[1] = "0";      // elevate later
    for (var i = 0; i < seredSeq.size(); ++i) {
      callArgs[i + 2] = seredSeq.get(i);
    }

    var elevate = ulift > 0 ? STR.".elevate(\{ulift})" : "";
    var onStuck = ExprializeUtils.makeNew(callName, callArgs);
    var finalArgs = fixed
      ? flatArgs.view().prepended(onStuck).joinToString()
      : STR."\{onStuck}, \{ExprializeUtils.makeImmutableSeq(CLASS_TERM, flatArgs)}";

    return STR."\{reducible}.invoke(\{finalArgs})\{elevate}";
  }

  @Override
  protected @NotNull String doSerialize(@NotNull Term term) {
    return switch (term) {
      case FreeTerm(var bind) -> {
        // It is possible that we meet bind here,
        // the serializer will instantiate some variable while serializing LamTerm
        var subst = binds.getOrNull(bind);
        if (subst == null) {
          throw new Panic(STR."No substitution for \{bind} during serialization");
        }

        yield subst;
      }
      case TyckInternal i -> throw new Panic(i.getClass().toString());
      case AppTerm appTerm -> makeAppNew(CLASS_APPTERM, appTerm.fun(), appTerm.arg());
      case LocalTerm _ when !allowLocalTerm -> throw new Panic("LocalTerm");
      case LocalTerm(var index) -> ExprializeUtils.makeNew(CLASS_LOCALTERM, Integer.toString(index));
      case LamTerm lamTerm -> ExprializeUtils.makeNew(CLASS_LAMTERM, serializeClosure(lamTerm.body()));
      case DataCall(var ref, var ulift, var args) -> ExprializeUtils.makeNew(CLASS_DATACALL,
        ExprializeUtils.getInstance(NameSerializer.getClassReference(ref)),
        Integer.toString(ulift),
        serializeToImmutableSeq(CLASS_TERM, args)
      );
      case ConCall(var head, var args) -> ExprializeUtils.makeNew(CLASS_CONCALL,
        ExprializeUtils.getInstance(NameSerializer.getClassReference(head.ref())),
        serializeToImmutableSeq(CLASS_TERM, head.ownerArgs()),
        Integer.toString(head.ulift()),
        serializeToImmutableSeq(CLASS_TERM, args)
      );
      case FnCall call -> {
        var ref = switch (call.ref()) {
          case JitFn jit -> ExprializeUtils.getInstance(NameSerializer.getClassReference(jit));
          case FnDef.Delegate def -> ExprializeUtils.getInstance(getClassReference(def.ref));
        };

        var ulift = call.ulift();
        var args = call.args();
        yield buildReducibleCall(ref, CLASS_FNCALL, ulift, ImmutableSeq.of(args), true);
      }
      case RuleReducer.Con conRuler -> buildReducibleCall(
        serializeApplicable(conRuler.rule()),
        CLASS_RULE_CON, conRuler.ulift(),
        ImmutableSeq.of(conRuler.dataArgs(), conRuler.conArgs()),
        false
      );
      case RuleReducer.Fn fnRuler -> buildReducibleCall(
        serializeApplicable(fnRuler.rule()),
        CLASS_RULE_FN, fnRuler.ulift(),
        ImmutableSeq.of(fnRuler.args()),
        false
      );
      case SortTerm(var kind, var ulift) -> ExprializeUtils.makeNew(ExprializeUtils.getJavaReference(SortTerm.class),
        ExprializeUtils.makeSub(CLASS_SORTKIND, kind.name()),
        Integer.toString(ulift));
      case PiTerm(var param, var body) -> ExprializeUtils.makeNew(ExprializeUtils.getJavaReference(PiTerm.class),
        doSerialize(param),
        serializeClosure(body)
      );
      case CoeTerm(var type, var r, var s) -> ExprializeUtils.makeNew(ExprializeUtils.getJavaReference(CoeTerm.class),
        serializeClosure(type),
        doSerialize(r),
        doSerialize(s)
      );
      case ProjTerm(var of, var ix) -> ExprializeUtils.makeNew(ExprializeUtils.getJavaReference(ProjTerm.class),
        doSerialize(of),
        Integer.toString(ix)
      );
      case PAppTerm(var fun, var arg, var a, var b) -> makeAppNew(ExprializeUtils.getJavaReference(PAppTerm.class),
        fun, arg, a, b
      );
      case EqTerm(var A, var a, var b) -> ExprializeUtils.makeNew(ExprializeUtils.getJavaReference(EqTerm.class),
        serializeClosure(A),
        doSerialize(a), doSerialize(b)
      );
      case DimTyTerm _ -> ExprializeUtils.getInstance(ExprializeUtils.getJavaReference(DimTyTerm.class));
      case DimTerm dim -> ExprializeUtils.makeSub(ExprializeUtils.getJavaReference(DimTerm.class), dim.name());
      case TupTerm(var items) -> ExprializeUtils.makeNew(ExprializeUtils.getJavaReference(TupTerm.class),
        serializeToImmutableSeq(CLASS_TERM, items)
      );
      case SigmaTerm sigmaTerm -> throw new UnsupportedOperationException("TODO");
      case PrimCall(var ref, var ulift, var args) -> ExprializeUtils.makeNew(CLASS_PRIMCALL,
        ExprializeUtils.getInstance(NameSerializer.getClassReference(ref)),
        Integer.toString(ulift),
        serializeToImmutableSeq(CLASS_TERM, args)
      );
      case IntegerTerm(var repr, var zero, var suc, var type) -> ExprializeUtils.makeNew(CLASS_INTEGER,
        Integer.toString(repr),
        ExprializeUtils.getInstance(NameSerializer.getClassReference(zero)),
        ExprializeUtils.getInstance(NameSerializer.getClassReference(suc)),
        doSerialize(type)
      );
      case ListTerm(var repr, var nil, var cons, var type) -> ExprializeUtils.makeNew(CLASS_LIST,
        ExprializeUtils.makeImmutableSeq(CLASS_TERM, repr.map(this::doSerialize), CLASS_PIMMSEQ),
        ExprializeUtils.getInstance(NameSerializer.getClassReference(nil)),
        ExprializeUtils.getInstance(NameSerializer.getClassReference(cons)),
        doSerialize(type)
      );
      case StringTerm stringTerm ->
        ExprializeUtils.makeNew(CLASS_STRING, ExprializeUtils.makeString(StringUtil.escapeStringCharacters(stringTerm.string())));
    };
  }

  // def f (A : Type) : Fn (a : A) -> A
  // (A : Type) : Pi(^0, IdxClosure(^1))
  // (A : Type) : Pi(^0, JitClosure(_ -> ^1))

  private @NotNull String with(@NotNull String subst, @NotNull Function<Term, String> continuation) {
    var bind = new LocalVar(subst);
    this.binds.put(bind, subst);
    var result = continuation.apply(new FreeTerm(bind));
    this.binds.remove(bind);
    return result;
  }

  private @NotNull String serializeClosure(@NotNull Closure body) {
    return serializeClosure(nameGen.nextName(null), body);
  }

  private @NotNull String serializeClosure(@NotNull String param, @NotNull Closure body) {
    return ExprializeUtils.makeNew(CLASS_JITLAMTERM, STR."\{param} -> \{with(param, t -> doSerialize(body.apply(t)))}");
  }

  @Override public @NotNull String serialize(Term unit) {
    binds.clear();
    var vars = ImmutableSeq.fill(instantiates.size(), i -> new LocalVar(STR."arg\{i}"));
    unit = unit.instantiateTeleVar(vars.view());
    vars.forEachWith(instantiates, binds::put);

    return doSerialize(unit);
  }
}
