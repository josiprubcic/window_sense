# windows_sense

Web aplikacija za pametni sustav automatskog upravljanja prozorima i roletama prema vremenskim uvjetima.

## Tehnologije

- Java 21
- Spring Boot 3
- Maven
- PostgreSQL 17
- Flyway
- HTML, CSS i JavaScript frontend
- ThingsBoard HTTP Device API priprema

## Pokretanje

Lokalni `local` profil ocekuje PostgreSQL 17. Najjednostavnije ga je pokrenuti Docker Composeom:

```bash
docker compose up -d postgres
```

Zatim pokrenite aplikaciju:

```bash
mvn spring-boot:run
```

Aplikacija je dostupna na:

```text
http://localhost:3000
```

## Testovi

```bash
mvn test
```

## Konfiguracija

```bash
export PORT=3000
export SPRING_PROFILES_ACTIVE=local
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/windowsense
export SPRING_DATASOURCE_USERNAME=windowsense
export SPRING_DATASOURCE_PASSWORD=windowsense
export THINGSBOARD_HOST=https://thingsboard.cloud
export THINGSBOARD_ACCESS_TOKEN=DEVICE_ACCESS_TOKEN
export THINGSBOARD_SYNC_ENABLED=true
export WINDOWSENSE_DEVICE_ID=windowsense-esp32-01
mvn spring-boot:run
```

Za produkciju koristite `prod` profil i postavite datasource kroz environment varijable,
bez hardkodiranja credentialsa:

```bash
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL=jdbc:postgresql://<neon-host>/<database>?sslmode=require
export SPRING_DATASOURCE_USERNAME=<neon-user>
export SPRING_DATASOURCE_PASSWORD=<neon-password>
```

Hibernate schema generation je ugasen za izmjene sheme:

```text
spring.jpa.hibernate.ddl-auto=validate
```

Shema se mijenja kroz Flyway migracije u `src/main/resources/db/migration`.

### Skalabilni rooms API

Prva faza DB-backed modela dodaje sobe i virtualne WindowSense uredjaje bez diranja
legacy single-window endpointa.

```text
GET /api/rooms
POST /api/rooms
```

Primjer:

```bash
curl -X POST http://localhost:3000/api/rooms \
  -H "content-type: application/json" \
  -d '{"name":"Spavaca soba"}'
```

`POST /api/rooms` pronalazi ili kreira lokalnog korisnika iz Auth0 identiteta,
pronalazi ili kreira njegov default home, kreira sobu i jedan virtualni
WindowSense uredjaj. ThingsBoard provisioning je u ovoj fazi mock/no-op servis
koji generira `tbAssetId` i `tbDeviceId`; stvarni ThingsBoard REST API jos se ne zove.

### OIDC login

OIDC je opcionalan za lokalni razvoj. Kada je ukljucen, web aplikacija i `/api/**`
endpointi traze login, osim `/api/health`, `/api/device/**`, Swagger UI-ja i
OpenAPI specifikacije.

U OIDC provideru dodajte redirect URI:

```text
http://localhost:3000/login/oauth2/code/windowsense
```

Primjer pokretanja:

```bash
export OIDC_ENABLED=true
export OIDC_ISSUER_URI=https://issuer.example.com/
export OIDC_CLIENT_ID=windowsense
export OIDC_CLIENT_SECRET=client-secret
export OIDC_SCOPES=openid,profile,email
mvn spring-boot:run
```
