FROM node:22-alpine AS build

WORKDIR /app

# Kopiera npm-manifest (räcker med package.json, ev. package-lock om du lägger till den senare)
COPY package*.json ./
RUN npm install

# Kopiera resten av frontend-koden
COPY . .

# Build - Railway skickar environment variables, men vi måste explicit säkerställa att de är tillgängliga
# Vite läser VITE_* variabler från process.env vid build-time
# Railway skickar environment variables till container, men för build måste vi använda --build-arg eller ENV
# Eftersom Railway inte stödjer --build-arg direkt, använder vi ENV som ärvs från Railway's environment
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

# Debug output för att verifiera att variabeln är satt
RUN echo "Building with VITE_API_BASE_URL=${VITE_API_BASE_URL}"

RUN npm run build

FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]


