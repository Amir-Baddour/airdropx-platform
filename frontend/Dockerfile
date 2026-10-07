# --- Build stage ---
FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
# Baked in at build time since Next.js inlines NEXT_PUBLIC_* vars into the client bundle — see README
# if you need to point a prebuilt image at a different backend URL without rebuilding.
ARG NEXT_PUBLIC_API_URL=http://localhost:8080
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
RUN npm run build

# --- Runtime stage ---
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

COPY --from=build /app/public ./public
COPY --from=build --chown=appuser:appgroup /app/.next/standalone ./
COPY --from=build --chown=appuser:appgroup /app/.next/static ./.next/static

USER appuser
EXPOSE 3000
ENV PORT=3000
CMD ["node", "server.js"]
