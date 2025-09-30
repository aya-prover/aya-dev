# Annotations

We use `@Bound` and `@Closed` to annotates the term a method can handle/will return,
however, most places don't have unique bound/closed-licit (FIXME please). For those places,
they mostly inherit from the inputs, for example, `Term#elevate` is a closed term if the term itself is already closed.

Most `Term` data structures don't annotate bound/closed-licit of their sub-`Term`, as they will be bound during tyck.
