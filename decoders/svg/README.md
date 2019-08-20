Picasso SVG Image Decoder
====================================

An image decoder that allows Picasso to decode SVG images.

Usage
-----

Provide an instance of `SvgImageDecoder` when creating a `Picasso` instance.

```java
Picasso p = new Picasso.Builder(context)
    .addImageDecoder(new SvgImageDecoder())
    .build();
```
