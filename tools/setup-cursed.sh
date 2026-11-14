#!/usr/bin/env bash
# The whole job without an AI assistant: find the TV, switch off what the TV maker runs that you do not need (you read the
# list first), put the launcher on and make it Home, and push the Cursed theme. It is tools/install-cursed.sh --debloat.
#
# Usage: tools/setup-cursed.sh [<tv ip>] [--list FILE] [--no-build] [--yes]
#        tools/setup-cursed.sh <tv ip> --rollback     undo all of it
# Every option is the installer's: tools/install-cursed.sh --help.
exec "$(cd "$(dirname "$0")" && pwd)/install-cursed.sh" --debloat "$@"
