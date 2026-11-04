# No npm dependencies: the image contains only Node.js and src/.
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY package.json ./
COPY src ./src
USER node
EXPOSE 8787
CMD ["node", "src/index.js"]
