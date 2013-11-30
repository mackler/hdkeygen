hdkeygen
========

This is a simple commnd-line utility for generating _hierarchical
determinstic wallet_ bitcoin keys as described in
[Bitcoin Improvement Proposal Number 32](https://en.bitcoin.it/wiki/BIP_0032).
In particular, this program is designed to be a part of the use case described as
_unsecure money receiver_, in which a less-secure machine, _e.g._, a
web server, is capable of generating addresses to receive payments,
but does not have the private keys necessary to sign spends from those
addresses.

HD keys are so called because they exist as nodes within a key
hierarchy, the root node of which is derived from your (hopefully)
secret _master seed_, consisting of (hopefully) random bits of data.
This program does _not_ generate a master seed for you, rather you
must already have your master seed, and it must be encrypted with [AES
256](http://en.wikipedia.org/wiki/Advanced_Encryption_Standard).

From the _Master Node_ are derived numbered accounts, each with a
position starting at zero and identified by a 32-bit integer.  Each
account, in turn, has two _keypair chains_: one _external_ and one
_internal_, numbered 0 and 1 respectively.  All the actual addresses
that are used are in one of these two keypair chains.  Addresses from
external chains are used to communicate publicly, _e.g._, for
receiving payments from other people, and addresses from the internal
chains are used, _e.g._, for receiving change when you create spends.
This program generates only the master keys for external chains.

HD keys are known as _extended keys_ because they contain all the data
involved in a regular bitcoin EC key, plus some more data that is
necessary to derive new keys in the hierarchy.  A public key that can
be used by your less-secure machine consists of two components:

1. the public key itself
2. The so-called _chain code_

An extended key can be specified by its path in the hierarchy.  An
apostrophe in a path segment indicates a private derivation.

Generate Your Master Seed
-------------------------

You can create your master seed however you want, and you have many
options.  The only requirement for using it with this program is that
it be encrypted with
[Advanced Encryption Standard](](http://en.wikipedia.org/wiki/Advanced_Encryption_Standard))
using a 256 bit key.  Here is just one suggestion for how to do it.
This may or may not work depending on your operating system:

    dd if=/dev/random count=1 bs=32 | gpg --symmetric --cipher-algo AES256 --output seed.aes

The above command-line will obtain 256 random bits, then ask you for a
password, encrypt the bits using the password, then save the result in
a file name `seed.aes`, which is where this program will look by
default.  For more details on master seeds, see the
[BIP32 document](https://en.bitcoin.it/wiki/BIP_0032#Master_key_generation).

Using This Application
----------------

Once you have a Master Seed, you are ready to use this program to
generate and write the file-pairs for any number of external HD public
key chains.  Any one of these file-pairs can then be uploaded to and
used as the master public key of an external address chain on your
less-secure server machine.

The names of the files generated are:

    externalMaster<account>.publicKey
    externalMaster<account>.chainCode
	
Where _account_ is the account position number you specify with the
`--account` option when you run the program, which defaults to `0`.

You can generate files for multiple keys by specifying the number of
such keys by using the `--count` option, which defaults to `1`.

What to do with the File Pair
-----------------------------

A public extended key can be deserialized from the generated file pair
using the [bitcoinj](https://code.google.com/p/bitcoinj/) library like
this:

    val deserializedPubKey: DeterministicKey =
	  HDKeyDerivation.createMasterPubKeyFromBytes(pubKeyBytes,chainCode)

Then the children of this public extended key will be the addresses
you can share with others:

    val ecKey: ECKey = childPubKey.toECKey

More About the _Master Seed_
----------------------------

When you run this program, your encrypted seed data can either be in a
file, or else passed as the sole command-line argument.  If it's in a
file, you can specify the name of the file using the `--seedfile` or `-s`
command-line option, or simply name the file `seed.aes` where this
program will look by default.  If you wish to pass the seed data on
the command line (and please be aware of the security implication of
doing so), then it must be encoded in
[Crockford Base32](http://www.crockford.com/wrmg/base32.html) format,
from which whitespace will be stripped before decoding.

If your run this program and provide the seed data in a file, then
this program will encode them using Crockford base-32 encoding and
display the result for your future reference.

Building This Application
-------------------------

After you have cloned this repository, build from the project directory:

    sbt stage

This will create the shell script under `target` in the
`universal/stage/bin` directory.

Example Usage
-------------

If you have a `seed.aes` file, and you want to generate a single
file-pair for account #0, then run with no command-line options:

    target/universal/stage/bin/hdkeygen

If you want to generate file-pars for account numbers 3, 4 and 5
(omitting the path of the executable):

    hgkeygen --account 3 --count 3

There are other command-line options.  Use `--help` for details.

Dependency: `gpg`
-----------------

In addition to the scala libraries specified in the `build.sbt` file,
this program also requires you have installed `gpg`, the OpenPGP
encryption and signing tool, which is part of the
[GNU Privacy Guard](http://www.gnu.org/software/gnupg/) suite of
programs.  This program (`hgkeygen`) expects `gpg` to be installed in
`/usr/bin/gpg`.  If you have `gpg` installed somewhere else, then
simply change the reference to that file in `Main.scala` before
compiling.

**Important**: make sure that wherever this program thinks you have
`gpg` installed, you actually have `gpg` installed, because whatever
is there, this program will try to execute it, and you could become
the victim of a security violation if a malicious hacker installs a
trojan horse in place of `gpg`.
