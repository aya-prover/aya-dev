// Copyright (c) 2020-2025 Tesla (Yinsen) Zhang.
// Use of this source code is governed by the MIT license that can be found in the LICENSE.md file.
package org.aya.compiler.serializers;

import kala.collection.immutable.ImmutableSeq;
import kala.collection.mutable.MutableLinkedHashMap;
import kala.tuple.Tuple;
import kala.tuple.Tuple2;
import org.aya.compiler.FieldRef;
import org.aya.compiler.MethodRef;
import org.aya.compiler.morphism.Constants;
import org.aya.compiler.morphism.FreeJavaResolver;
import org.aya.compiler.morphism.ast.*;
import org.aya.generic.stmt.Shaped;
import org.aya.prettier.FindUsage;
import org.aya.syntax.core.Closure;
import org.aya.syntax.core.def.FnDef;
import org.aya.syntax.core.def.Matchy;
import org.aya.syntax.core.term.*;
import org.aya.syntax.core.term.call.*;
import org.aya.syntax.core.term.marker.TyckInternal;
import org.aya.syntax.core.term.repr.*;
import org.aya.syntax.core.term.xtt.*;
import org.aya.syntax.ref.LocalVar;
import org.aya.util.Panic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.aya.compiler.morphism.Constants.LAMBDA_NEW;

/**
 * Build the "constructor form" of {@link Term}, but in Java.
 */
public class TermSerializer extends AbstractExprSerializer<Term> {
  public static final @NotNull FieldRef TYPE0_FIELD = FreeJavaResolver.resolve(SortTerm.class, "Type0");
  public static final @NotNull FieldRef ISET_FIELD = FreeJavaResolver.resolve(SortTerm.class, "ISet");

  /// Parameters of the function being serialized, if not null
  private final @Nullable ImmutableSeq<AstVariable> argTerms;
  /// Terms that should be instantiated
  private final @NotNull ImmutableSeq<AstVariable> instantiates;
  private final @NotNull MutableLinkedHashMap<LocalVar, AstVariable> binds;

  /// Whether allow {@link LocalTerm}, false in default (in order to report unexpected LocalTerm)
  private final boolean allowLocalTerm;

  public TermSerializer(
    @NotNull AstCodeBuilder builder,
    @NotNull SerializerContext context,
    @Nullable ImmutableSeq<AstVariable> argTerms,
    @NotNull ImmutableSeq<AstVariable> instantiates
  ) {
    this(builder, context, argTerms, instantiates, false);
  }

  public TermSerializer(
    @NotNull AstCodeBuilder builder,
    @NotNull SerializerContext context,
    @Nullable ImmutableSeq<AstVariable> argTerms,
    @NotNull ImmutableSeq<AstVariable> instantiates,
    boolean allowLocalTerm
  ) {
    super(builder, context);
    this.argTerms = argTerms;
    this.instantiates = instantiates;
    this.allowLocalTerm = allowLocalTerm;
    this.binds = MutableLinkedHashMap.of();
  }

  private TermSerializer(
    @NotNull AstCodeBuilder builder,
    @NotNull SerializerContext context,
    @NotNull MutableLinkedHashMap<LocalVar, AstVariable> newBinds,
    boolean allowLocalTerm
  ) {
    super(builder, context);
    this.instantiates = ImmutableSeq.empty();
    this.binds = newBinds;
    this.argTerms = null;
    this.allowLocalTerm = allowLocalTerm;
  }

  private @NotNull AstVariable serializeApplicable(@NotNull Shaped.Applicable<?> applicable) {
    return switch (applicable) {
      case IntegerOps.ConRule conRule -> builder.mkNew(IntegerOps.ConRule.class, ImmutableSeq.of(
        getInstance(conRule.ref()),
        doSerialize(conRule.zero())
      ));
      case IntegerOps.FnRule fnRule -> builder.mkNew(IntegerOps.FnRule.class, ImmutableSeq.of(
        getInstance(fnRule.ref()),
        builder.refEnum(fnRule.kind())
      ));
      case ListOps.ConRule conRule -> builder.mkNew(ListOps.ConRule.class, ImmutableSeq.of(
        getInstance(conRule.ref()),
        doSerialize(conRule.empty())
      ));
      default -> Panic.unreachable();
    };
  }

  public static @NotNull AstVariable buildFnCall(
    @NotNull AstCodeBuilder builder, @NotNull Class<?> callName, @NotNull FnDef def,
    int ulift, @NotNull ImmutableSeq<AstVariable> args
  ) {
    return builder.mkNew(callName, ImmutableSeq.of(
      getInstance(builder, def),
      new AstExpr.Iconst(ulift),
      AbstractExprSerializer.makeImmutableSeq(builder, Term.class, args)
    ));
  }

  private @NotNull AstVariable getNormalizer() {
    var normalizer = context.normalizer();
    if (normalizer == null) {
      normalizer = Constants.unaryOperatorIdentity(builder);
    }

    return normalizer;
  }

  private @NotNull AstVariable
  buildFnInvoke(@NotNull ClassDesc defClass, int ulift, @NotNull ImmutableSeq<AstVariable> args) {
    var normalizer = getNormalizer();
    var invokeExpr = FnSerializer.makeInvoke(builder, defClass, normalizer, args);
    // builder.markUsage(defClass, ClassHierarchyResolver.ClassHierarchyInfo.ofClass(CD_JitFn));

    if (ulift != 0) {
      return builder.invoke(Constants.ELEVATE, invokeExpr, ImmutableSeq.of(new AstExpr.Iconst(ulift)));
    } else return invokeExpr;
  }

  // There is a chance I need to add lifting to match, so keep a function for us to
  // add the if-else in it
  private @NotNull AstVariable buildMatchyInvoke(
    @NotNull ClassDesc matchyClass,
    @NotNull ImmutableSeq<AstValue> args,
    @NotNull ImmutableSeq<AstVariable> captures
  ) {
    var normalizer = getNormalizer();
    // builder.markUsage(matchyClass, ClassHierarchyResolver.ClassHierarchyInfo.ofClass(CD_JitMatchy));
    return MatchySerializer.makeInvoke(builder, matchyClass, normalizer, captures, args);
  }

  /// UNPURE
  @Override protected @NotNull AstVariable doSerialize(@NotNull Term term) {
    return switch (term) {
      case FreeTerm(var bind) -> {
        // It is possible that we meet bind here,
        // the serializer will instantiate some variable while serializing LamTerm
        var subst = binds.getOrNull(bind);
        if (subst == null) {
          throw new Panic("No substitution for " + bind + " during serialization");
        }

        yield subst;
      }
      // LetFreeTerm should NOT appear
      case TyckInternal i -> throw new Panic(i.getClass().toString());
      case Callable.SharableCall call when call.ulift() == 0 && call.args().isEmpty() ->
        getCallInstance(CallKind.from(call), call.ref());
      case ClassCall(var ref, var ulift, var args) -> builder.mkNew(ClassCall.class, ImmutableSeq.of(
        getInstance(ref),
        new AstExpr.Iconst(ulift),
        serializeClosureToImmutableSeq(args)
      ));
      case MemberCall call -> makeMemberNew(call);
      case AppTerm(var fun, var arg) -> makeAppNew(AppTerm.class, fun, arg);
      case LocalTerm _ when !allowLocalTerm -> throw new Panic("LocalTerm");
      case LocalTerm(var index) -> builder.mkNew(LocalTerm.class, ImmutableSeq.of(new AstExpr.Iconst(index)));
      case LamTerm lamTerm -> builder.mkNew(LAMBDA_NEW, ImmutableSeq.of(serializeClosure(lamTerm.body())));
      case DataCall(var ref, var ulift, var args) -> builder.mkNew(DataCall.class, ImmutableSeq.of(
        getInstance(ref),
        new AstExpr.Iconst(ulift),
        serializeToImmutableSeq(Term.class, args)
      ));
      case ConCall(var head, var args) -> builder.mkNew(ConCall.class, ImmutableSeq.of(
        getInstance(head.ref()),
        serializeToImmutableSeq(Term.class, head.ownerArgs()),
        new AstExpr.Iconst(head.ulift()),
        serializeToImmutableSeq(Term.class, args)
      ));
      /// Assumption: `term.tailCall() == true` implies `unit == term.ref()`
      case FnCall call when argTerms != null && call.tailCall() -> {
        var args = call.args().map(this::doSerialize);
        // call.tailCall() == true means:
        // * [call] is the body of [unit]
        // * [call] is the let body of some let which is the body of [unit]
        // thus the returned [AstVariable] is used by caller, and we can return a dummy caller as long as the caller never uses it.
        assert argTerms.size() == args.size();
        // Will cause conflict in theory, but won't in practice due to current local variable
        // declaration heuristics.
        argTerms.forEachWith(args, (a, b) ->
          builder.updateVar(a, new AstExpr.Ref(b)));
        builder.continueLoop();
        yield new AstVariable.Local(-1);
      }
      case FnCall(var ref, var ulift, var args, _) ->
        buildFnInvoke(NameSerializer.getClassDesc(ref), ulift, args.map(this::doSerialize));
      case RuleReducer.Con(var rule, int ulift, var ownerArgs, var conArgs) -> {
        var onStuck = builder.mkNew(RuleReducer.Con.class, ImmutableSeq.of(
          serializeApplicable(rule),
          new AstExpr.Iconst(ulift),
          serializeToImmutableSeq(Term.class, ownerArgs),
          serializeToImmutableSeq(Term.class, conArgs)
        ));
        yield builder.invoke(Constants.RULEREDUCER_MAKE, onStuck, ImmutableSeq.empty());
      }
      case RuleReducer.Fn(var rule, int ulift, var args) -> {
        var onStuck = builder.mkNew(RuleReducer.Fn.class, ImmutableSeq.of(
          serializeApplicable(rule),
          new AstExpr.Iconst(ulift),
          serializeToImmutableSeq(Term.class, args)
        ));
        yield builder.invoke(Constants.RULEREDUCER_MAKE, onStuck, ImmutableSeq.empty());
      }
      case SortTerm sort when sort.equals(SortTerm.Type0) -> builder.refField(TYPE0_FIELD);
      case SortTerm sort when sort.equals(SortTerm.ISet) -> builder.refField(ISET_FIELD);
      case SortTerm(var kind, var ulift) ->
        builder.mkNew(SortTerm.class, ImmutableSeq.of(builder.refEnum(kind), new AstExpr.Iconst(ulift)));
      case DepTypeTerm(var kind, var param, var body) -> builder.mkNew(DepTypeTerm.class, ImmutableSeq.of(
        builder.refEnum(kind),
        doSerialize(param),
        serializeClosure(body)
      ));
      case CoeTerm(var type, var r, var s) -> builder.mkNew(CoeTerm.class, ImmutableSeq.of(
        serializeClosure(type),
        doSerialize(r),
        doSerialize(s)
      ));
      case ProjTerm(var of, var fst) -> builder.mkNew(ProjTerm.class, ImmutableSeq.of(
        doSerialize(of),
        new AstExpr.Bconst(fst)
      ));
      case PAppTerm(var fun, var arg, var a, var b) -> makeAppNew(PAppTerm.class,
        fun, arg, a, b
      );
      case EqTerm(var A, var a, var b) -> builder.mkNew(EqTerm.class, ImmutableSeq.of(
        serializeClosure(A),
        doSerialize(a), doSerialize(b)
      ));
      case DimTyTerm _ -> builder.refEnum(DimTyTerm.INSTANCE);
      case DimTerm dim -> builder.refEnum(dim);
      case TupTerm(var l, var r) -> builder.mkNew(TupTerm.class, ImmutableSeq.of(
        doSerialize(l), doSerialize(r)
      ));
      case PrimCall(var ref, var ulift, var args) -> builder.mkNew(PrimCall.class, ImmutableSeq.of(
        getInstance(ref),
        new AstExpr.Iconst(ulift),
        serializeToImmutableSeq(Term.class, args)
      ));
      case IntegerTerm(var repr, _, _, var type) -> builder.invoke(
        new MethodRef(
          NameSerializer.getClassDesc(type.ref()),
          AyaSerializer.METHOD_MAKE_INTEGER,
          Constants.CD_IntegerTerm,
          ImmutableSeq.of(ConstantDescs.CD_int),false),
        ImmutableSeq.of(new AstExpr.Iconst(repr))
      );
      case ListTerm(var repr, var nil, var cons, var type) -> builder.mkNew(ListTerm.class, ImmutableSeq.of(
        makeImmutableSeq(builder, Constants.IMMTREESEQ, Term.class, repr.map(this::doSerialize)),
        getInstance(nil),
        getInstance(cons),
        doSerialize(type)
      ));
      case StringTerm stringTerm -> builder.mkNew(StringTerm.class, ImmutableSeq.of(
        new AstExpr.Sconst(stringTerm.string())
      ));
      case ClassCastTerm(var classRef, var subterm, var rember, var forgor) ->
        builder.mkNew(ClassCastTerm.class, ImmutableSeq.of(
          getInstance(classRef),
          serialize(subterm),
          serializeClosureToImmutableSeq(rember),
          serializeClosureToImmutableSeq(forgor)
        ));
      case MatchCall(var ref, var args, var captures) -> {
        if (ref instanceof Matchy matchy) context.recorder().addMatchy(matchy, args.size(), captures.size());
        yield buildMatchyInvoke(NameSerializer.getClassDesc(ref),
          args.map(this::doSerialize), captures.map(this::doSerialize));
      }
      case NewTerm(var classCall) -> builder.mkNew(NewTerm.class, ImmutableSeq.of(doSerialize(classCall)));
      case PartialTyTerm(var lhs, var rhs, var A) -> builder.mkNew(PartialTyTerm.class, ImmutableSeq.of(
        doSerialize(lhs),
        doSerialize(rhs),
        doSerialize(A)
      ));
      case PartialTerm(var element) -> builder.mkNew(PartialTerm.class, ImmutableSeq.of(doSerialize(element)));
      case LetTerm(var definedAs, var body) -> {
        var defVar = new LocalVar("<let>");
        var letDef = doSerialize(definedAs);
        yield with(defVar, letDef, () -> doSerialize(body.apply(defVar)));
      }
    };
  }

  private @NotNull AstVariable makeLambda(
    @NotNull MethodRef lambdaType,
    @NotNull BiFunction<AstArgsProvider.Lambda, TermSerializer, AstVariable> cont
  ) {
    var binds = MutableLinkedHashMap.from(this.binds);
    var entries = binds.toImmutableSeq();
    var fullCaptures = entries.view().map(Tuple2::component2);
    boolean hasNormalizer = context.normalizer() != null;
    if (hasNormalizer) {
      fullCaptures = fullCaptures.prepended(context.normalizer());
    }

    return builder.mkLambda(fullCaptures.toSeq(), lambdaType, (ap, builder1) -> {
      var normalizer = hasNormalizer ? InvokeSignatureHelper.normalizer(ap) : null;
      var newContext = new SerializerContext(normalizer, context.recorder());
      var captured = entries.mapIndexed((i, tup) -> {
        var capturedExpr = hasNormalizer ? InvokeSignatureHelper.capture(ap, i) : ap.capture(i);
        return Tuple.of(tup.component1(), capturedExpr);
      });
      var result = cont.apply(ap, new TermSerializer(builder1, newContext,
        MutableLinkedHashMap.from(captured), this.allowLocalTerm));
      builder1.returnWith(result);
    });
  }

  private @NotNull AstVariable makeClosure(@NotNull BiFunction<TermSerializer, AstVariable, AstVariable> cont) {
    return makeLambda(Constants.CLOSURE, (ap, te) -> {
      var casted = te.builder.checkcast(ap.arg(0), Constants.CD_Term);
      return cont.apply(te, casted);
    });
  }

  private @NotNull AstVariable serializeClosureToImmutableSeq(@NotNull ImmutableSeq<Closure> cls) {
    return makeImmutableSeq(Closure.class, cls.map(this::serializeClosure));
  }

  private @NotNull AstVariable with(
    @NotNull LocalVar var,
    @NotNull AstVariable subst,
    @NotNull Supplier<AstVariable> continuation
  ) {
    this.binds.put(var, subst);
    var result = continuation.get();
    this.binds.remove(var);
    return result;
  }

  private @NotNull AstVariable serializeClosure(@NotNull Closure body) {
    if (body instanceof Closure.Const(var inside)) return serializeConst(inside);

    var var = new LocalVar("<jit>");
    var appliedBody = body.apply(var);
    if (FindUsage.free(appliedBody, var) == 0) return serializeConst(appliedBody);

    var closure = makeClosure((te, arg) ->
      te.with(var, arg, () -> te.doSerialize(appliedBody)));

    return builder.mkNew(Closure.Jit.class, ImmutableSeq.of(closure));
  }

  private @NotNull AstVariable serializeConst(@NotNull Term appliedBody) {
    return builder.invoke(Constants.CLOSURE_MKCONST, ImmutableSeq.of(doSerialize(appliedBody)));
  }

  private @NotNull AstVariable makeAppNew(@NotNull Class<?> className, Term... terms) {
    var obj = builder.mkNew(className, ImmutableSeq.from(terms).map(this::doSerialize));
    return builder.invoke(Constants.BETAMAKE, obj, ImmutableSeq.empty());
  }

  private @NotNull AstVariable makeMemberNew(@NotNull MemberCall call) {
    var obj = builder.mkNew(MemberCall.class, ImmutableSeq.of(
      doSerialize(call.of()),
      getInstance(call.ref()),
      new AstExpr.Iconst(call.ulift()),
      serializeToImmutableSeq(Term.class, call.projArgs())
    ));

    return builder.invoke(Constants.BETAMAKE, obj, ImmutableSeq.empty());
  }

  @Override public @NotNull AstVariable serialize(Term unit) {
    binds.clear();
    var vars = ImmutableSeq.fill(instantiates.size(), i -> new LocalVar("arg" + i));
    unit = unit.instTeleVar(vars.view());
    vars.forEachWith(instantiates, binds::put);

    return doSerialize(unit);
  }
}
