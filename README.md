# A fork of SecureSocial

This is a fork of [SecureSocial](https://github.com/jaliss/securesocial), originally developped by Jorge Aliss. We heavily use this plugin for one of our projects, and we sometimes want to change it. Even though We send pull requests, we don't want to stop our development unitil they are merged, so we decided to fork it. Basically, we send a PR of every change, but it's up to the original author whether the change is merged or not.

Below is the original README as of Mar 12, 2015:  
[README-original.textile](README-original.textile)

# Installation

## Play! 2.3

Add the dependencies:

```
"tv.kazu" %% "securesocial" % "3.0.5"
```

Then, implement `UserService` etc. See [the sample projects](samples/).

## Play! 2.2 and before

Use the versions by the original author for Play! 2.2 and 2.1. See [the doc](http://securesocial.ws/guide/getting-started.html).

# Changelog

* 3.0.5 (2016-05-12)
    * Fixed bug related to "scope" introduced in 3.0.4
* 3.0.4 (2015-12-08)
    * Added "login" to BasicProfile#extraInfo for GitHub accounts
	* Enabled to add "scope" param to ProviderController.authenticate
* 3.0.3 (2015-12-01)
    * Added BitbucketProvider
* 3.0.2 (2015-06-04)
    * Added ExtraInfo to BasicProfile
    * Merged changes in jaliss/securesocial#525
* 3.0.1 (2015-03-31)
    * Added SlackProvider
* 3.0.0 (2015-03-12)
    * Forked from the original version (3.0-M3)
    * A bit of refactoring
