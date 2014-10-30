Picasso[![Build Status](https://travis-ci.org/danhantao/square/picasso.svg?branch=master)](https://travis-ci.org/square/picasso)
=======

A powerful image downloading and caching library for Android

![](website/static/sample.png)

For more information please see [the website][1]



Download
--------

Download [the latest JAR][2] or grab via Gradle:
```groovy
compile 'com.squareup.picasso:picasso:2.3.4'
```
or Maven:
```xml
<dependency>
    <groupId>com.squareup.picasso</groupId>
    <artifactId>picasso</artifactId>
    <version>2.3.4</version>
</dependency>
```


ProGuard
--------

If you are using ProGuard make sure you add the following option:

```
-dontwarn com.squareup.okhttp.**
```



License
--------

    Copyright 2013 Square, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.


 [1]: http://square.github.io/picasso/
 [2]: http://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=com.squareup.picasso&a=picasso&v=LATEST
