#!/bin/bash

# Start Redis
redis-server --daemonize yes --port 6379 2>/dev/null
sleep 1

# Build Spring Boot if needed
if [ ! -f /home/runner/workspace/backend/target/todo-app-1.0.0.jar ]; then
  echo "Building Spring Boot..."
  cd /home/runner/workspace/backend
  mvn package -q -DskipTests
fi

# Start Spring Boot
echo "Starting Spring Boot..."
java -jar /home/runner/workspace/backend/target/todo-app-1.0.0.jar &
BACKEND_PID=$!

# Wait for Spring Boot
echo "Waiting for Spring Boot to be ready..."
for i in $(seq 1 40); do
  if curl -sf -o /dev/null http://localhost:8080/api/auth/me 2>/dev/null || curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/auth/me 2>/dev/null | grep -qE "401|403"; then
    echo "Spring Boot is ready!"
    break
  fi
  sleep 2
done

# Start Node.js frontend (Express + Vite dev server on port 5000)
echo "Starting frontend..."
cd /home/runner/workspace
exec npm run dev
