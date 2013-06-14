Change Log
==========

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
