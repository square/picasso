Picasso Layoutlib Compat
====================================

Provides a ThreadFactory which when paired with Layoutlib will
execute its Thread's in time for snapshot rendering. This is 
useful for Paparazzi tests and compose-ui-tooling's @Preview

Usage
-----

Attach the [LayoutLibThreadFactory] to the [PicassoExecutorService] when initializing Picasso.

```kotlin
val picasso = Picasso.Builder(context)
  .executor(PicassoExecutorService(threadFactory = LayoutLibThreadFactory()))
  .build()

```