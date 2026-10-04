
FROM node:22-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build


FROM node:22-alpine

WORKDIR /app

ENV NODE_ENV=production \
    PORT=4200

COPY --from=build --chown=node:node /app/dist/frontend ./dist/frontend

USER node

EXPOSE 4200

CMD ["node", "dist/frontend/server/server.mjs"]
