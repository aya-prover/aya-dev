# Proposal of the priority system

We've all agreed upon that number-based priority system is hard to use and error-prone.  So I propose a new system based on a partial order.

The user can declare how their operator binds compared to existing ones using the following syntax (keywords to be discussed)

```mzi
\bind * \tighter +
\bind + , - \looser /
\bind ** \tighter * , /
```

And the system will take note of all such declarations and organize them into a graph.
Then it

1. Ensures the graph is **acyclic**.
2. Performs **transitive closure** operation on this graph to obtain a partial order.
3. The system does not require all priority to be decided; but if a use actually occurs where the priority is ambiguous, an error is reported.
   The user can solve this problem by manually adding parentheses, or declare a priority locally.

This eliminates the need of memorizing priorities of common operations & floating-point priorities.

## Function applications

We either insist that function applications always binds most tightly so that for example `f x + y` always means `(f x) + y`, or have a special priority for them so that the user can write `\bind ** \tighter \app` to make `f x ** y` mean `f (x ** y)`.  I think the former is more clear, easier to implement, and does not contradict mathematical convention (since in mathematics people always use parentheses for function application, i.e., they write `f (x ** y)` anyway).

## Scoping

Such declaration are exported by default, but can also be local to the file (`\bind \local * \tighter +`?)
