FROM node:24-alpine AS assembly
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
ARG CAENIS_BACKEND_URL=http://core:8080
ENV CAENIS_BACKEND_URL=$CAENIS_BACKEND_URL
COPY package.json pnpm-lock.yaml ./
RUN corepack enable && corepack prepare pnpm@10.12.1 --activate && pnpm install --frozen-lockfile --ignore-scripts
COPY . .
RUN pnpm exec next build

FROM node:24-alpine
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 HOSTNAME=0.0.0.0 PORT=3000
COPY --from=assembly --chown=node:node /app/.next/standalone ./
COPY --from=assembly --chown=node:node /app/.next/static ./.next/static
COPY --from=assembly --chown=node:node /app/public ./public
USER node
EXPOSE 3000
CMD ["node","server.js"]
