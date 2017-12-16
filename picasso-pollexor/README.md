Picasso Pollexor Request Transformer
====================================

A request transformer which uses a remote [Thumbor][1] install to perform
image transformation on the server.


Usage
-----

Create a `PollexorRequestTransformer` using the remote host and optional encryption key.

```java
RequestTransformer transformer =
    new PollexorRequestTransformer("http://example.com", "secretpassword");
```

Pass the transformer when creating a `Picasso` instance.

```java
Picasso p = new Picasso.Builder(context)
    .requestTransformer(transformer)
    .build();
```

_Note: This can only be used with an instance you create yourself. You cannot set a request
transformer on the global singleton instance (`Picasso.get`)._



 [1]: https://github.com/globocom/thumbor
