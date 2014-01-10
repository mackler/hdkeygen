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
must already have your master seed, and it must be encrypted with a
symmetric cipher that `gpg` supports, such as [AES
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

Public Versus Private Derivation
--------------------------------

Each node in the key hierarchy other than the master root has a
child-number relative to its parent represented as an unsigned integer
within a range having the same maximum as a native signed integer,
namely 2^31 - 1.  The bit that would otherwise be used to indicate the
sign is instead used to indicate public (if 0) or private derivation
(if 1) derivation.  Thus, each node can have children numbered from 0
to 2147483647, inclusive.

There is a security implication between using private versus public
derivation, namely, as
[the spec](https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki#security)
says:

> knowledge of the extended public key + a private key descending from
> it is equivalent to knowing the extended private key (i.e., every
> private and public key) in case public derivation is used.

Generate Your Master Seed
-------------------------

You can create your master seed however you want, and you have many
options.  The only requirement for using it with this program is that
it be encrypted with a symmetric cipher supported by `gpg`, such
as [Advanced Encryption
Standard](](http://en.wikipedia.org/wiki/Advanced_Encryption_Standard)).
Here is a suggestion for how to do it.  This may or may not
work depending on your operating system:

    dd if=/dev/random count=1 bs=32 | gpg --symmetric --cipher-algo AES256 --output seed.aes

The above command-line will obtain 256 random bits, then ask you for a
password, encrypt the bits using the password, then save the result in
a file name `seed.aes`, which is where this program will look by
default.

Another way is to apply the SHA 256 hash function to a file of random
data:

    openssl dgst -sha256 -binary /var/log/messages | gpg --symmetric --cipher-algo AES256 --output seed.aes

For more details on master seeds, see the
[BIP32 document](https://en.bitcoin.it/wiki/BIP_0032#Master_key_generation).

Using This Application
----------------------

Once you have a Master Seed, you are ready to use this program to
generate and write the key files for any number of HD wallet accounts.
For each account, two file-pairs will be written, one pair containing
the private key for the account (both external and internal chains),
the other pair containing the public key for the external chain only.
The public-external file-pair can then be uploaded to your less-secure
server machine and used to generate Bitcoin addresses for receiving
payments.  Meanwhile, on your more-secure machine you use the
private-account file-pair to generate keys in the external and
internal chains.  Keys in the external chain will have the private key
data necessary for spending payments that people send to Bitcoin
addresses generated on your less-secure server, while the internal
chain can be used to generate change addresses.

The names of the files generated for each account are:

    account<account>.privateKey
    account<account>.chaincode
    externalChain<account>.publicKey
    externalChain<account>.chaincode


Where _account_ is the account number you specify with the `--account`
option when you run the program, which defaults to `0`.

The private key file will be encrypted using AES-256.  To be clear:
the private key is the parent node of the external-chain public key,
and the external-chain public key is child number zero of the private
key.

You can generate key-files for multiple accounts by specifying the number of
such accounts using the `--count` option, which defaults to `1`.

What to do with the Key Files
-----------------------------

For each account, you get four files, two for the more-secure machine,
two for the less-secure machine.  In each file-pair, one file is the
key bytes, the other is the extension "chain code" data.

A public extended key can be deserialized from the generated file pair
using the [bitcoinj](https://code.google.com/p/bitcoinj/) library like
this (in Scala):

    val rootKey: DeterministicKey =
	  HDKeyDerivation.createMasterPubKeyFromBytes(pubKeyBytes,chainCode)

Then construct a new chain rooted at the deserialized key:

    val hierarchy = new DeterministicHierarchy(rootKey)

Then you can use the `deriveChild` method to obtain extended keys in the
chain:

    val emptyPath        = List[ChildNumber]() // path to the parent
	val notRelative      = false               // whether the path is relative to the root path
	val dontCreateParent = false               // whether to create the parent corresponding to path
    val childKey: DeterministicKey = hierarchy.deriveChild (
	                                   emptyPath,
				                       notRelative,
				                       dontCreateParent,
				                       new ChildNumber(childNumber: Int)
                                     )

Alternatively, you can use the static `HDKeyDerivation.deriveChildKey` method:

	val childKey: DeterministicKey =
	  HDKeyDerivation.deriveChildKey(rootKey, cn: ChildNumber)

Then use `toECKey` and `toAddress` to get the regular Bitcoin key and
address from each extended key:

    val newAddress: Address = childKey.toECKey.toAddress(params: NetworkParameters)

Use the private key-pair similarly:

    HDKeyDerivation.createMasterPrivKeyFromBytes(privKeyBytes, chainCode)
	
Then, the zeroth child of that key will be the external chain of the
account, but including the private key data necessary for signing spends.

More About the _Master Seed_
----------------------------

When you run this program, your encrypted seed data can either be in a
file, or else passed as the sole command-line argument.  If it's in a
file, you can specify the name of the file using the `--seedfile` or
`-s` command-line option, or simply name the file `seed.aes` where
this program will look by default.  If you wish to pass the encrypted
seed data on the command line (and please be aware of the security
implication of doing so), then it must be encoded in
[Crockford Base32](http://www.crockford.com/wrmg/base32.html) format,
from which whitespace will be stripped before decoding.

If your run this program and provide the seed data in a file, then
this program will display the Crockford base-32 encoding for your
future reference.

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

If you want to generate key files for accounts 3 and 4 (omitting the
path of the executable):

    hgkeygen --account 3 --count 2

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
trojan horse where this program thinks the `gpg` executable is.
