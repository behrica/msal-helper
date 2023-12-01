#!/bin/sh
set -e

on_exit () {
	[ $? -eq 0 ] && exit
	echo 'ERROR: Feature "Clojure (via asdf)" (ghcr.io/devcontainers-contrib/features/clojure-asdf) failed to install! Look at the documentation at ${documentation} for help troubleshooting this error.'
}

trap on_exit EXIT

set -a
. ../devcontainer-features.builtin.env
. ./devcontainer-features.env
set +a

echo ===========================================================================

echo 'Feature       : Clojure (via asdf)'
echo 'Description   : Clojure is a dialect of Lisp, and shares with Lisp the code-as-data philosophy and a powerful macro system.'
echo 'Id            : ghcr.io/devcontainers-contrib/features/clojure-asdf'
echo 'Version       : 2.0.14'
echo 'Documentation : http://github.com/devcontainers-contrib/features/tree/main/src/clojure-asdf'
echo 'Options       :'
echo '    VERSION="latest"'
echo 'Environment   :'
printenv
echo ===========================================================================

chmod +x ./install.sh
./install.sh
