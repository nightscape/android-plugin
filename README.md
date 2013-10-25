# Build Scala Android apps using Scala

sbt-android-plugin is an extension for the Scala build tool [sbt][] which
aims to make it as simple as possible to get started with Scala on Android.

Together with [giter8][] you can create and build a simple Android Scala project in a
matter of minutes.

Utilize the giter8 template [jlaws/android-plugin](https://github.com/jlaws/android-app.g8) 
to setup a project shell with SBT 0.13, Scala 2.10.3, and sbt eclipse

## Getting started

See the [Getting started][] guide on the wiki for more documentation. In case
you're not not familiar with sbt make sure to check out its excellent
[Getting Started Guide][sbt-getting-started] first.

## Hacking on the plugin

If you need make modifications to the plugin itself, you can compile
and install it locally (you need at least sbt 0.11.x to build it):

    $ git clone git://github.com/jlaws/android-plugin.git
    $ cd android-plugin
    $ sbt publish-local

## Mailing list

There's no official mailing list for the project but most contributors hang
out in [scala-on-android][] or [simple-build-tool][].

You can also check out a list of
[projects using sbt-android-plugin][] to see some real-world examples.

## Credits

This code is based on work by Walter Chang
([saisiyat](http://github.com/weihsiu/saisiyat/)), turned into a plugin by
[Mark Harrah](http://github.com/harrah), and maintained by
[Jan Berkel](https://github.com/jberkel).

A lot of people have contributed to the plugin; see [contributors][] for a full
list.

[![Build Status](https://secure.travis-ci.org/jberkel/android-plugin.png?branch=master)](http://travis-ci.org/jberkel/android-plugin)

[sbt]: https://github.com/harrah/xsbt/wiki
[scala-on-android]: http://groups.google.com/group/scala-on-android
[simple-build-tool]: http://groups.google.com/group/simple-build-tool
[contributors]: https://github.com/jlaws/android-plugin/wiki/Contributors
[homebrew]: https://github.com/mxcl/homebrew
[projects using sbt-android-plugin]: https://github.com/jlaws/android-plugin/wiki/Projects-using-sbt-android-plugin
[Getting started]: https://github.com/jlaws/android-plugin/wiki/getting-started
[giter8]: https://github.com/n8han/giter8
[sbt-getting-started]: http://scala-sbt.org/release/docs/Getting-Started/Welcome
