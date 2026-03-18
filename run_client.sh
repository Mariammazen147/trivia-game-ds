
#run Game client
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

HOST="${1:-localhost}"
PORT="${2:-5000}"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/java}"
JAVA="${JAVA:-java}"

if [ ! -d "out/client" ]; then
    echo "Compiled classes not found. Running compile.sh first"
    bash compile.sh
fi

echo "Connecting to $HOST:$PORT"
"$JAVA" -cp "out" client.Client "$HOST" "$PORT"
