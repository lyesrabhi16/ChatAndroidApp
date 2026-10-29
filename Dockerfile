FROM node:22-slim
WORKDIR /app
COPY package.json package-lock.json ./
# argon2 ships a native module: it is installed here, for the platform the container runs on
RUN npm ci --omit=dev
COPY server.js ./
COPY server ./server
# uploaded files (images, voice notes, documents) are stored here: mount a volume to keep them
RUN mkdir -p /app/uploads && chown node:node /app/uploads
EXPOSE 8001 8000
USER node
CMD ["node", "server.js"]
