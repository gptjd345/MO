FROM node:20-slim AS frontend-build

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY frontend/ frontend/
COPY script/ script/
COPY vite.config.ts tsconfig.json tailwind.config.ts postcss.config.js components.json ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend-build

WORKDIR /app/backend
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B
COPY backend/src ./src
RUN mvn package -DskipTests -q

FROM node:20-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jre-headless \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY --from=frontend-build /app/dist/index.cjs ./dist/index.cjs
COPY --from=frontend-build /app/dist/public ./dist/public
COPY --from=backend-build /app/backend/target/todo-app-1.0.0.jar ./backend/target/todo-app-1.0.0.jar

ENV NODE_ENV=production
ENV PORT=5000

EXPOSE 5000

CMD ["node", "dist/index.cjs"]
