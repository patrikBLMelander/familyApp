# Statisk sida, ingen byggkedja.
#
# Här stod tidigare en tvåstegs Vite-build av PWA:n. Webbappen är avvecklad och
# ersatt av en informationssida, så det finns ingenting att kompilera -- bara
# filer att servera. React-koden ligger kvar under src/ men byggs inte längre;
# att ta bort den är ett eget beslut och inte en bieffekt av det här.
#
# Att samma server svarar på både Railway-adressen och kidquest.se är poängen:
# apparna länkar till /privacy och /villkor på Railway-adressen, och en
# installerad app som aldrig uppdateras pekar dit för alltid. Med en server och
# ett innehåll behövs inga omdirigeringar som kan sluta fungera.
FROM nginx:1.27-alpine

COPY site/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
