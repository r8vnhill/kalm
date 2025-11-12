#!/usr/bin/env bash

set -euo pipefail

print_usage() {
  cat <<'USAGE'
Usage: invoke_gradle_with_jdk.sh [options] [--] [Gradle arguments]

Runs the Gradle wrapper from the repository root with an optional JAVA_HOME override.

Options:
  -j, --jdk PATH              Path to the JDK home directory to use for the invocation.
  -w, --working-directory DIR Directory containing the Gradle wrapper. Defaults to the
                              repository root (one level above this script).
  -h, --help                  Show this help message and exit.

All remaining arguments after options are passed to gradlew. If no arguments are
provided, the script defaults to the "help" task.
USAGE
}

resolve_path() {
  local path="$1"
  if command -v realpath >/dev/null 2>&1; then
    realpath -m "$path"
  else
    (
      cd "$path" >/dev/null 2>&1 && pwd -P
    )
  fi
}

JDK_PATH=""
WORKING_DIRECTORY="$(dirname "${BASH_SOURCE[0]}")/.."
declare -a GRADLE_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    -j|--jdk)
      if [[ $# -lt 2 ]]; then
        echo "Error: --jdk requires a path argument." >&2
        exit 2
      fi
      JDK_PATH="$2"
      shift 2
      ;;
    -w|--working-directory)
      if [[ $# -lt 2 ]]; then
        echo "Error: --working-directory requires a directory argument." >&2
        exit 2
      fi
      WORKING_DIRECTORY="$2"
      shift 2
      ;;
    -h|--help)
      print_usage
      exit 0
      ;;
    --)
      shift
      GRADLE_ARGS=("$@")
      break
      ;;
    *)
      GRADLE_ARGS+=("$1")
      shift
      ;;
  esac
done

if [[ ${#GRADLE_ARGS[@]} -eq 0 ]]; then
  GRADLE_ARGS=("help")
fi

if [[ -z "$WORKING_DIRECTORY" ]]; then
  echo "Error: Working directory not provided." >&2
  exit 2
fi

if [[ ! -d "$WORKING_DIRECTORY" ]]; then
  echo "Error: Working directory '$WORKING_DIRECTORY' was not found." >&2
  exit 2
fi

RESOLVED_WORKING_DIR="$(resolve_path "$WORKING_DIRECTORY")"
GRADLE_WRAPPER="$RESOLVED_WORKING_DIR/gradlew"

if [[ ! -f "$GRADLE_WRAPPER" ]]; then
  echo "Error: Gradle wrapper not found at '$GRADLE_WRAPPER'." >&2
  exit 2
fi

ORIGINAL_JAVA_HOME="${JAVA_HOME-}"
JDK_WAS_SET=0

if [[ -n "$JDK_PATH" ]]; then
  if [[ ! -d "$JDK_PATH" ]]; then
    echo "Error: JDK path '$JDK_PATH' was not found." >&2
    exit 2
  fi
  RESOLVED_JDK="$(resolve_path "$JDK_PATH")"
  export JAVA_HOME="$RESOLVED_JDK"
  JDK_WAS_SET=1
  echo "Using JAVA_HOME: $JAVA_HOME"
elif [[ -z "${JAVA_HOME-}" ]]; then
  echo "Warning: JAVA_HOME is not set; Gradle will rely on toolchains or discovery." >&2
else
  echo "Using existing JAVA_HOME: $JAVA_HOME"
fi

restore_java_home() {
  if [[ $JDK_WAS_SET -eq 1 ]]; then
    if [[ -n "$ORIGINAL_JAVA_HOME" ]]; then
      export JAVA_HOME="$ORIGINAL_JAVA_HOME"
    else
      unset JAVA_HOME
    fi
  fi
}

trap restore_java_home EXIT

SECONDS=0
COMMAND_OUTPUT=0

pushd "$RESOLVED_WORKING_DIR" >/dev/null
set +e
"$GRADLE_WRAPPER" "${GRADLE_ARGS[@]}"
COMMAND_OUTPUT=$?
set -e
popd >/dev/null

DURATION=$SECONDS

printf 'Command          : %s\n' "$GRADLE_WRAPPER"
printf 'Arguments        : %s\n' "${GRADLE_ARGS[*]}"
printf 'WorkingDirectory : %s\n' "$RESOLVED_WORKING_DIR"
printf 'JavaHomeBefore   : %s\n' "${ORIGINAL_JAVA_HOME-}"
printf 'JavaHomeApplied  : %s\n' "${JAVA_HOME-}"
printf 'ExitCode         : %d\n' "$COMMAND_OUTPUT"
printf 'Success          : %s\n' "$([[ $COMMAND_OUTPUT -eq 0 ]] && echo true || echo false)"
printf 'DurationSeconds  : %d\n' "$DURATION"

exit "$COMMAND_OUTPUT"
