# Rimic

Rimic is a fork of [Jumble](https://github.com/acomminos/Jumble), an Android
service implementation of the Mumble protocol which was originally written by
Andrew Comminos as the backend of [Plumble](https://github.com/acomminos/Plumble).
Rimic is the backend of [WiMic](https://github.com/hiro2233/rimic_android).

## About Jumble

The primary goal of the Jumble project is to encourage developers to embrace
the Mumble protocol on Android through a free, complete, and stable
implementation. At the moment, development is focused on improving stability
and security.

Prior to the release of Jumble, all implementations of the Mumble protocol on
Android have been using the same non-free code developed by @pcgod. To ensure
the unencumbered use of Jumble, no sources or derivatives will be copied from
that project.

## Including in your project

Rimic is a standard Android library project using the gradle build system.
[See here for instructions on how to include it into your gradle project](http://tools.android.com/tech-docs/new-build-system/user-guide#TOC-Referencing-a-Library).

Currently, there is no tutorial to integrate Rimic with your project. In the
mean time, please examine the exposed interface IRimicService as well as
Wimic's implementation.

## License

Rimic is now licensed under the GNU GPL v3+. See [LICENSE](LICENSE).
