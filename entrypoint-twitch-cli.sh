#!/bin/sh

# Start mock API in background
twitch mock-api start --host 0.0.0.0 --port 8080 &

# Start websocket server in background
twitch event websocket start-server --ip 0.0.0.0 --port 8081 &

# Start a simple web server to trigger events
# This will listen on port 8082 and execute twitch commands
qa-trigger-server &

# Wait for any process to exit
wait -n

# Exit with status of process that exited first
exit $?
