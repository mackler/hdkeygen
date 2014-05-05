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
must either (1) already have your master seed, and it must be
encrypted with a symmetric cipher that `gpg` supports, such as
[AES 256](http://en.wikipedia.org/wiki/Advanced_Encryption_Standard),
or else (2) run this program in "brain-wallet" mode, in which the seed is
derived from a pass-phrase.

From the _Master Node_ are derived numbered accounts, each with a
position starting at zero and identified by a 32-bit integer.  Each
account, in turn, has two _keypair chains_: one _external_ and one
_internal_, numbered 0 and 1 respectively.  All the actual addresses
that are used for receiving Bitcoin are in one of these two keypair
chains.  Addresses from external chains are used to communicate
publicly, _e.g._, for receiving payments from other people, and
addresses from the internal chains are used, _e.g._, for receiving
change when you create spends.

HD keys are known as _extended keys_ because they contain all the data
involved in a regular bitcoin EC key, plus some more data that is
necessary to derive new keys in the hierarchy.  A public key that can
be used by your less-secure machine consists of two components:

1. the public key itself
2. The so-called _chain code_

An extended key can be specified by its path in the hierarchy,
beginning with `M` to indicate the master root.  An apostrophe in a
path segment indicates a "hardened" derivation.

Normal Versus Hardened Derivation
--------------------------------

Each node in the key hierarchy other than the master root has a
child-number relative to its parent, which child-number is represented
as an unsigned integer within a range having the same maximum as a
native signed integer, namely 2^31 - 1.  The bit that would otherwise
be used to indicate the sign is instead used to indicate (if 0) normal
or (if 1) so-called _hardened_ derivation.  Thus, each node can have
children numbered from 0 to 2147483647, inclusive, with normal child
indexed represented by integers 0 to 2147483647, and hardened child
indexes represented (internally) by signed integers -2147483648 to -1.

There is a security implication between using normal versus hardened
derivation, namely, as
[the spec](https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki#security)
says:

> knowledge of the extended public key + any non-hardened private key
> descending from it is equivalent to knowing the extended private key
> (and thus every private and public key descending from it). This means
> that extended public keys must be treated more carefully than regular
> public keys. It is also the reason for the existence of hardened keys,
> and why they are used for the account level in the tree. This way, a
> leak of account-specific (or below) private key never risks
> compromising the master or other accounts.

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

    openssl dgst -sha256 -binary /var/log/httpd.access | gpg --symmetric --cipher-algo AES256 --output seed.aes

For more details on master seeds, see the
[BIP32 document](https://en.bitcoin.it/wiki/BIP_0032#Master_key_generation).

Brain Wallet
------------

An alternative to generating a secret seed is the so-called brain
wallet, where instead of encrypting your secret seed with a pass
phrase, the pass-phrase _is_ the seed (effectively).  This means that
as long as you don't forget the pass-phrase, you cannot "lose" the
seed.  One disadvantage (of several) is that an attacker does not have
to acquire your encrypted seed before attempting to crack your
pass-phrase; in other words you get one-factor instead of two-factor
authentication.  Another problem is that if you choose the same
pass-phrase as someone else, then you'll both get the same seed, and
thus the same key hierarchy.

Therefore, before using a brain wallet, be sure to
[understand how to choose your phrase](https://en.bitcoin.it/wiki/Brainwallet)
or else your bitcoins **will be taken by someone else.**

Use the `-b` switch to run this program in brain-wallet mode.  Your
pass-phrase will be echoed, so make sure no bad guys watch as you
type.

Using This Application
----------------------

Once you have a Master Seed or a brain-wallet phrase, you are ready to
use this program to generate and write the key files for any number of
HD wallet accounts.  For each account, two files will be written, one
file containing the private key for the account (both external and
internal chains), the other file containing the public key for the
**external chain only**.  The public-external file can then be
uploaded to your less-secure server machine and used to generate
Bitcoin addresses for you to receive payments.  Meanwhile, on your
more-secure machine you use the private-account file to generate keys
in the both external and internal chains.  Keys in the external chain
will have the private key data necessary for spending Bitcoin that
people have sent to those Bitcoin addresses that you generated on your
less-secure server, while the internal chain can be used to generate
change addresses.

The names of the files generated for each account are:

    account<serial>-<fingerprint>_priv.aes
    externalChain<serial>-<fingerprint>_pub.hdk

Where _serial_ is the account number you specify with the `--account`
option when you run the program, which defaults to `0`, and
_fingerprint_ is the (probably) unique 4-byte fingerprint of the
account node's HD key, represented as an **unsigned** integer. *Note:
the fingerprint number in the name of the external chain file is
that of the account key, not the external chain key.*

The private key file will be encrypted using AES-256.  To be clear:
the private key is the parent node of the external-chain public key,
and the external-chain public key is child number zero of the private
key.

You can generate key-files for multiple accounts by specifying the number of
such accounts using the `--count` option, which defaults to `1`.

What to do with the Key Files
-----------------------------

For each account, you get two files, one for the more-secure machine,
one for the less-secure machine.

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

Limitation
----------

When generating more than one account, the same spending password is
used for all accounts.  If you want differetn passwords for each
account, you must run the program once for each distinct password.
