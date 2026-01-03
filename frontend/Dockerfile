FROM node:22-alpine AS build

WORKDIR /app

# Kopiera npm-manifest (räcker med package.json, ev. package-lock om du lägger till den senare)
COPY package*.json ./
RUN npm install

# Kopiera resten av frontend-koden
COPY . .

# Build - Vite läser automatiskt VITE_* variabler från environment vid build-time
# Railway skickar automatiskt environment variables till build-steget
RUN npm run build

FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]


