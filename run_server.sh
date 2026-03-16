#!/bin/bash
# Run the Trivia Game server
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

GSON_JAR="lib/gson-2.10.1.jar"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/java}"
JAVA="${JAVA:-java}"

if [ ! -d "out/server" ]; then
    echo "Compiled classes not found. Running compile.sh first..."
    bash compile.sh
fi

echo "Starting Trivia Game Server..."
"$JAVA" -cp "out:$GSON_JAR" server.Server
