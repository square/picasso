Picasso
=======

**Attention**: This library is deprecated.
Please use alternatives like [Coil](https://coil-kt.github.io/coil/) for future projects, and start planning to migrate existing projects, especially if they rely on Compose UI.
Existing versions will continue to function, but no new work is planned.
While some changes may land in the repo as we support internal legacy usage and migration, there will be no more public releases to Maven Central.

Thank you to all who used and/or contributed to Picasso over its decade of image loading.

---

A powerful image downloading and caching library for Android

![](website/static/sample.png)

For more information please see [the website][1]



Download
--------

Download the latest AAR from [Maven Central][2] or grab via Gradle:
```groovy
implementation 'com.squareup.picasso:picasso:2.8'
```
or Maven:
```xml
<dependency>
  <groupId>com.squareup.picasso</groupId>
  <artifactId>picasso</artifactId>
  <version>2.8</version>
</dependency>
```

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].

Picasso requires at minimum Java 8 and API 21.


ProGuard
--------

If you are using ProGuard you might need to add OkHttp's rules: https://github.com/square/okhttp/#r8--proguard



License
--------

    Copyright 2013 Square, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


 [1]: https://square.github.io/picasso/
 [2]: https://search.maven.org/search?q=g:com.squareup.picasso%20AND%20a:picasso
 [snap]: https://s01.oss.sonatype.org/content/repositories/snapshots/
