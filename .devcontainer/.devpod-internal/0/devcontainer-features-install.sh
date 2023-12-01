#!/bin/sh
set -e

on_exit () {
	[ $? -eq 0 ] && exit
	echo 'ERROR: Feature "Bash Command" (ghcr.io/devcontainers-contrib/features/bash-command) failed to install! Look at the documentation at ${documentation} for help troubleshooting this error.'
}

trap on_exit EXIT

set -a
. ../devcontainer-features.builtin.env
. ./devcontainer-features.env
set +a

echo ===========================================================================

echo 'Feature       : Bash Command'
echo 'Description   : Executes a bash command'
echo 'Id            : ghcr.io/devcontainers-contrib/features/bash-command'
echo 'Version       : 1.0.0'
echo 'Documentation : https://github.com/devcontainers-contrib/features/tree/main/src/bash-command'
echo 'Options       :'
echo '    COMMAND="apt-get update && apt-get install -y rlwrap"'
echo 'Environment   :'
printenv
echo ===========================================================================

chmod +x ./install.sh
./install.sh
