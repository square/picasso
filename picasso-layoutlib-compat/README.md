Picasso Layoutlib Compat
====================================

Provides a ThreadFactory to manage thread execution from Handlers in situations
driven by layoutlib, e.g., Paparazzi tests and compose-ui-tooling's @Preview

Usage
-----

Pass an instance of [LayoutLibExecutorService] when initializing Picasso.

```kotlin
val picasso = Picasso.Builder(context)
  .executor(LayoutLibExecutorService())
  .build()
```