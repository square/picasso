Change Log
==========

Version 2.2.0 *(2014-01-31)*
----------------------------

 * Add support decoding various contact photo URIs. 
 * Add support for loading `android.resource` URIs (e.g. load assets from other packages).
 * Add support for MICRO/MINI thumbnails for media images.
 * Add API to supply custom `Bitmap.Config` for decoding.
 * Performance: Reduce GC by reusing same `StringBuilder` instance on main thread for key creation.
 * Performance: Reduce default buffer allocation to 4k for `MarkableInputStream`.
 * Fix: Detect and decode WebP streams from byte array.
 * Fix: Non-200 HTTP responses will now display error drawable if supplied.
 * Fix: All exceptions during decode will now dispatch a failure.
 * Fix: Catch `OutOfMemory` errors, dispatch a failure, and output stats in logcat.
 * Fix: `fit()` now handles cases where either width or height was not zero.
 * Fix: Prevent crash from `null` intent on `NetworkBroadcastReceiver`.
 * Fix: Honor exif orientation when no custom transformations supplied.
 * Fix: Exceptions during transformations propagate to the main thread. 
 * Fix: Correct skia decoding problem during underflow.
 * Fix: Placeholder uses full bounds.


Version 2.1.1 *(2013-10-04)*
----------------------------

 * `Target` now has callback for applying placeholder. This makes it symmetric with image views when
   using `into()`.
 * Fix: Another work around for Android's header decoding algorthm readin more than 4K of image data
   when decoding bounds.
 * Fix: Ensure default network-based executor is unregistered when instance is shut down.
 * Fix: Ensure connection is always closed for non-2xx response codes.


Version 2.1.0 *(2013-10-01)*
----------------------------

*Duplicate of v2.0.2. Do not use.*


Version 2.0.2 *(2013-09-11)*
----------------------------

 * Fix: Additional work around for Android's header decoding algorithm reading more than 4K of image
   data when decoding bounds.


Version 2.0.1 *(2013-09-04)*
----------------------------

 * Enable filtered bitmaps for higher transform quality.
 * Fix: Using callbacks with `into()` on `fit()` requests are now always invoked.
 * Fix: Ensure final frame of cross-fade between place holder and image renders correctly.
 * Fix: Work around Android's behavior of reading more than 1K of image header data when decoding
   bounds for some images.


Version 2.0.0 *(2013-08-30)*
----------------------------

 * New architecture distances Picasso further from the main thread using a dedicated dispatcher
   thread to manage requests.
 * Request merging. Two requests on the same key will be combined and the result will be delivered
   to both at the same time.
 * `fetch()` requests are now properly wired up to be used as "warm up the cache" type of requests
   without a target.
 * `fit()` will now automatically wait for the view to be measured before executing the request.
 * `shutdown()` API added. Clears the memory cache and stops all threads. Submitting new requests
   will cause a crash after `shutdown()` has been called.
 * Batch completed requests to the main thread to reduce main thread re-layout/draw calls.
 * Variable thread count depending on network connectivity. The faster the network the more threads
   and vice versa.
 * Ability to specify a callback with `ImageView` requests.
 * Picasso will now decode the bounds of the target bitmap over the network. This helps avoid
   decoding 2000x2000 images meant for 100x100 views.
 * Support loading asset URIs in the form `file:///android_asset/...`.
 * BETA: Ability to rewrite requests on the fly. This is useful if you want to add custom logic for
   wiring up requests differently.


Version 1.1.1 *(2013-06-14)*
----------------------------

 * Fix: Ensure old requests for targets are cancelled when using a `null` image.


Version 1.1.0 *(2013-06-13)*
----------------------------

 * `load` method can now take a `Uri`.
 * Support loading contact photos given a contact `Uri`.
 * Add `centerInside()` image transformation.
 * Fix: Prevent network stream decodes from blocking each other.


Version 1.0.2 *(2013-05-23)*
----------------------------

 * Auto-scale disk cache based on file system size.
 * `placeholder` now accepts `null` for clearing an existing image when used in an adapter and
   without an explicit placeholder image.
 * New global failure listener for reporting load errors to a remote analytics or crash service.
 * Fix: Ensure disk cache folder is created before initialization.
 * Fix: Only use the built-in disk cache on API 14+ (but you're all using [OkHttp][1] anyways,
   right?).


Version 1.0.1 *(2013-05-14)*
----------------------------

 * Fix: Properly set priority for download threads.
 * Fix: Ensure stats thread is always initialized.


Version 1.0.0 *(2013-05-14)*
----------------------------

Initial release.




 [1]: http://square.github.io/okhttp/
