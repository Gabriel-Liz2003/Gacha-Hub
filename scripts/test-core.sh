#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
output_dir="$(mktemp -d)"
trap 'rm -rf "$output_dir"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -d "$output_dir" app/src/main/java/dev/gachahub/core/ResourceMath.java tests/CoreTest.java
java -cp "$output_dir" CoreTest
