# Annotations

We use `@Bound` and `@Closed` to annotates the term a method can handle/will return,
however, most places don't have clear db-closeness. For those places,
they mostly inherit from the inputs, for example, `Term#elevate` is a closed term if the term itself is already closed.

Most `Term` data structures don't annotate the db-closeness of their sub-`Term`, as they will be bound during tyck.

`@Bound` and `@Closed` may also apply to data structures with `Term`, such as `Jdg` or `Pat`
