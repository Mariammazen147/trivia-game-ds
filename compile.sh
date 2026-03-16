#!/bin/bash
# Compile the Trivia Game server and client
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

GSON_JAR="lib/gson-2.10.1.jar"
OUT_DIR="out"

# Locate javac: prefer system PATH, then known user/extension locations
if command -v javac &>/dev/null; then
    JAVAC="javac"
elif [ -x "$HOME/java/jdk-current/jdk-21.0.9+10/bin/javac" ]; then
    JAVAC="$HOME/java/jdk-current/jdk-21.0.9+10/bin/javac"
elif [ -x "/home/mariam/.vscode/extensions/redhat.java-1.53.0-linux-x64/jre/21.0.10-linux-x86_64/bin/javac" ]; then
    JAVAC="/home/mariam/.vscode/extensions/redhat.java-1.53.0-linux-x64/jre/21.0.10-linux-x86_64/bin/javac"
    JAVA="/home/mariam/.vscode/extensions/redhat.java-1.53.0-linux-x64/jre/21.0.10-linux-x86_64/bin/java"
else
    echo "ERROR: javac not found. Please install a JDK (e.g. sudo apt install openjdk-21-jdk)."
    exit 1
fi

if [ ! -f "$GSON_JAR" ]; then
    echo "ERROR: $GSON_JAR not found."
    echo "Download it from:"
    echo "  https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
    exit 1
fi

mkdir -p "$OUT_DIR"

echo "Using javac: $JAVAC"
echo ""

echo "Compiling server classes..."
"$JAVAC" -cp "$GSON_JAR" -d "$OUT_DIR" server/*.java

echo "Compiling client class..."
"$JAVAC" -d "$OUT_DIR" client/*.java

echo ""
echo "Compilation successful! All class files are in: $OUT_DIR/"
echo ""
echo "To run the server:"
echo "  java -cp out:lib/gson-2.10.1.jar server.Server"
echo ""
echo "To run the client (in a separate terminal):"
echo "  java -cp out client.Client [host] [port]"
echo ""
echo "  (host defaults to localhost, port defaults to 5000)"
