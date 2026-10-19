#!/bin/bash
# Test-server helper for the phase 0B spike.
# usage: srv.sh start | cmd "<command>" | wait "<regex>" [timeout] | stop [timeout] | kill
S=${SPIKE_SERVER_DIR:?set SPIKE_SERVER_DIR to the test server directory}
case "$1" in
  start)
    rm -f "$S/in.fifo"; mkfifo "$S/in.fifo"
    cd "$S" || exit 1
    sleep infinity > "$S/in.fifo" &
    echo $! > "$S/keeper.pid"
    nohup java -Xmx4G $SPIKE_JAVA_OPTS -jar fabric-server-launch.jar nogui < "$S/in.fifo" > "$S/console.log" 2>&1 &
    echo $! > "$S/server.pid"
    ;;
  cmd) echo "$2" > "$S/in.fifo" ;;
  wait)
    t=${3:-300}
    for _ in $(seq 1 "$t"); do
      if grep -qE "$2" "$S/console.log" 2>/dev/null; then grep -E "$2" "$S/console.log" | tail -3; exit 0; fi
      sleep 1
    done
    echo "TIMEOUT waiting for $2"; exit 1 ;;
  stop)
    t=${2:-120}
    echo "stop" > "$S/in.fifo"
    pid=$(cat "$S/server.pid")
    for _ in $(seq 1 "$t"); do kill -0 "$pid" 2>/dev/null || { kill "$(cat "$S/keeper.pid")" 2>/dev/null; echo "EXITED"; exit 0; }; sleep 1; done
    echo "HUNG after ${t}s"; exit 1 ;;
  kill)
    kill "$(cat "$S/server.pid")" 2>/dev/null; sleep 5; kill -9 "$(cat "$S/server.pid")" 2>/dev/null
    kill "$(cat "$S/keeper.pid")" 2>/dev/null; echo "KILLED" ;;
esac
