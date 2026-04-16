FROM nginx:1.27-alpine

COPY index.html /usr/share/nginx/html/index.html
COPY fintrack.html /usr/share/nginx/html/fintrack.html
COPY app.js /usr/share/nginx/html/app.js
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 3000
