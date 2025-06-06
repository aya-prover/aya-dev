# 术语: Free Term

由于这个术语无法简短说明，因此使用一个单独的文件解释。

> 注意：这个术语指的不是 `class FreeTerm`，用于表示自由变量的 `Term`

## Bound Term

_Bound term_ 是指它含有尚未被捕获的绑定变量，例如函数 `const`: `fn a b => a` 的内部表示 `fn. fn. ^1` 中，单独的 `^1`
就是一个尚未被捕获的绑定变量，
而 `^1` 和 `fn. ^1` 就是 _bound term_。

_Bound Term_ 是通过 `bind`/`bindAt`/etc. 产生的，它通常不会被直接使用，需要放在一个定义了绑定变量的 Term 内部，如 `LamTerm`
或 `PiTerm`。

## Free Term

_Free term_ 是指它不含有任何尚未被捕获的绑定变量，例如函数 `const`: `fn a b => a` 的内部表示 `fn. fn. ^1` 中，`fn. fn. ^1`
整体就不含有
任何尚未被捕获的绑定变量。

_Free term_ 是绝大多数情况下使用的 Term，通常来说，你应该尽可能要求传入的 Term 是 _free_ 的，除非你可以处理 _bound term_。

## 局部无名的 STLC

不同于用字符串代表变量，局部无名中具有 自由变量 和 绑定变量，而它们需要用不同的上下文。我们约定，用 `Γ` 表示自由变量的上下文，用
`Δ` 表示绑定变量。

```
 (a : A) in Γ
--------------- (free-intro)
 Γ, Δ |- a : A
 
    Δ[n] = A
---------------- (bound-intro)
 Γ, Δ |- ^n : A
 
  Γ, (A, Δ) |- t : B
----------------------- (lam-intro)
 Γ, Δ |- fn. t : A → B
 
 Γ, Δ |- f : A → B    Γ, Δ |- a : A
------------------------------------ (app-intro)
          Γ, Δ |- f a : B 
```

> 忽略了一些无关紧要的前提

至此，我们可以用更形式化的方式解释 _bound term_ 和 _free term_：`t` 是 _bound term_ 指得是 **它无法存在于空的绑定上下文**；
类似的，`t` 是 _free term_ 指的是 **它能够存在于空的绑定上下文**。或者说，_free term_ 在绑定变量上是 _closed_ 的。

例如：`fn. ^1` 需要有 `Δ = A`， 而 `fn. fn. ^1` 只需要 `Δ = nil`。
