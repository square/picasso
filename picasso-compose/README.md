Picasso Compose Ui
====================================

A [Painter] which wraps a [RequestCreator]

Usage
-----

Create a `Painter` using the rememberPainter extension on a Picasso instance.

```kotlin
val picasso = Picasso.Builder(context).build()
val painter = picasso.rememberPainter(key = url) {
  it.load(url).placeholder(placeholderDrawable).error(errorDrawable)
}
```