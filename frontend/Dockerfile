FROM node:22-alpine AS build

WORKDIR /app

# Kopiera npm-manifest (räcker med package.json, ev. package-lock om du lägger till den senare)
COPY package*.json ./
RUN npm install

# Kopiera resten av frontend-koden
COPY . .

# Build - Vi använder runtime config istället av build-time env vars
# Så vi behöver inte VITE_API_BASE_URL här, den sätts i index.html och config.ts
RUN npm run build

FROM nginx:1.27-alpine

# Copy built files
COPY --from=build /app/dist /usr/share/nginx/html

# Copy nginx configuration for client-side routing
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]


