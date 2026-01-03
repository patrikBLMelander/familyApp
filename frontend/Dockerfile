FROM node:22-alpine AS build

WORKDIR /app

# Kopiera npm-manifest (räcker med package.json, ev. package-lock om du lägger till den senare)
COPY package*.json ./
RUN npm install

# Kopiera resten av frontend-koden
COPY . .

# Build - Vite läser VITE_* variabler från environment vid build-time
# Railway skickar environment variables, men vi måste explicit säkerställa att de är tillgängliga
# Använd ARG för att ta emot värdet vid build-time och ENV för att göra det tillgängligt
ARG VITE_API_BASE_URL
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

# Debug: visa vad som är satt (ta bort efter test)
RUN echo "VITE_API_BASE_URL is set to: $VITE_API_BASE_URL"

RUN npm run build

FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]


