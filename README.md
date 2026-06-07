# windows_sense

Web aplikacija za pametni sustav automatskog upravljanja prozorima i roletama prema vremenskim uvjetima.

## Tehnologije

- Java 21
- Spring Boot 3
- Maven
- PostgreSQL 17
- Flyway
- HTML, CSS i JavaScript frontend
- ThingsBoard provisioning, telemetry i server-side RPC command delivery

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
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/windowsense
export SPRING_DATASOURCE_USERNAME=windowsense
export SPRING_DATASOURCE_PASSWORD=windowsense
export THINGSBOARD_HOST=https://thingsboard.cloud
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

### Room-first rooms API

DB-backed model je room-first: soba se kreira samostalno, bez automatskog
WindowDevicea, ThingsBoard devicea ili simulacijskog stanja.

```text
GET /api/rooms
POST /api/rooms
POST /api/rooms/{roomId}/devices/virtual
POST /api/rooms/{roomId}/devices/pair
```

Primjer:

```bash
curl -X POST http://localhost:3000/api/rooms \
  -H "content-type: application/json" \
  -d '{"name":"Spavaca soba"}'
```

`POST /api/rooms` pronalazi ili kreira lokalnog korisnika iz Auth0 identiteta,
pronalazi ili kreira njegov default home i kreira samo sobu. Uredjaj se dodaje
naknadno preko room-specific endpointa za virtualni ili fizicki uredjaj.

Sobne komande se routeaju prema konkretnom `WindowDevice` uredjaju u sobi po
capabilityju. `target=window` trazi `WINDOW_CONTROL`, a `target=blinds` trazi
`BLINDS_CONTROL`. Ako vise aktivnih uredjaja u sobi podrzava isti target, frontend
mora poslati `localDeviceId`.

### Physical command delivery modes

Backend podrzava dva moda isporuke komandi za fizicke uredjaje:

```yaml
windowsense:
  commands:
    physical-delivery: polling
    rpc:
      enabled: false
      timeout-ms: 15000
      persistent: false
```

```yaml
windowsense:
  commands:
    physical-delivery: thingsboard-rpc
    rpc:
      enabled: true
      timeout-ms: 15000
      persistent: false
```

`polling` je default i zadrzava dev/fallback flow: ESP32 dohvada komande preko
`GET /api/esp/{serialNumber}/commands` i potvrdjuje ih preko
`POST /api/esp/{serialNumber}/ack`. Serijski broj se mapira na lokalni fizicki
uredjaj u sobi, pa nema fallbacka na globalni/default uredjaj.

`thingsboard-rpc` salje two-way server-side RPC na konkretni
`WindowDevice.tbDeviceId`:

```http
POST {THINGSBOARD_HOST}/api/plugins/rpc/twoway/{tbDeviceId}
```

Primjer RPC payload-a:

```json
{
  "method": "setBlindsPosition",
  "params": {
    "commandId": "cmd-...",
    "position": 85
  },
  "timeout": 15000,
  "persistent": false
}
```

ESP firmware za RPC mode mora biti spojen na ThingsBoard MQTT koristeci svoj
access token, slusati:

```text
v1/devices/me/rpc/request/+
```

i nakon stvarnog izvrsenja odgovoriti na:

```text
v1/devices/me/rpc/response/{requestId}
```

Primjer responsea:

```json
{
  "status": "EXECUTED",
  "commandId": "cmd-...",
  "blindClosedPercent": 85
}
```

Response nakon primitka komande nije isto sto i response nakon izvrsenja. Za
WindowSense RPC flow treba response nakon stvarne fizicke akcije.

Legacy endpointi `GET /api/device/commands?deviceId=<tbDeviceId>` i
`POST /api/device/ack?deviceId=<tbDeviceId>` su uklonjeni. Serijski broj nije
access token ni secret; device auth zaglavlje treba dodati kasnije prije
produkcijske upotrebe.

### ESP32 AP provisioning za fizicke uredjaje

Primarni flow za novi fizicki ESP32 je:

1. ESP se digne u AP modu i izlozi `GET http://192.168.4.1/api/provisioning-info`.
2. Korisnik u appu unese pairing code i spoji se na ESP WiFi.
3. Frontend povuce javne ESP podatke i posalje ih backendu na
   `POST /api/rooms/{roomId}/devices/provision-physical`.
4. Backend kreira ThingsBoard `DEVICE`, generira ThingsBoard access token, spremi ga
   encrypted i vrati samo kratkotrajni `provisioningSessionId`.
5. Frontend posalje ESP-u `POST http://192.168.4.1/api/provision` s WiFi podacima,
   `backendUrl` i `provisioningSessionId`.
6. ESP se spoji na WiFi i pozove `POST /api/device/bootstrap` sa svojim
   `serialNumber`, skrivenim `deviceSecret` i session ID-em. Tek tada backend vraca
   ThingsBoard token direktno ESP-u.

ESP `provisioning-info` payload koji app ocekuje:

```json
{
  "serialNumber": "WS-ESP32-0001",
  "hardwareId": "esp32-chip-id",
  "firmwareVersion": "1.0.0",
  "capabilities": ["window", "blinds", "rain", "lux", "temp", "wind"],
  "pairingCodeHash": "<sha256(normalized-pairing-code)>",
  "deviceSecretHash": "<sha256(device-secret)>"
}
```

ESP bootstrap payload prema backendu:

```json
{
  "serialNumber": "WS-ESP32-0001",
  "deviceSecret": "<secret stored on ESP>",
  "provisioningSessionId": "<short-lived session from app>"
}
```

ThingsBoard MQTT host se moze eksplicitno postaviti kroz `THINGSBOARD_MQTT_HOST`.
Ako nije postavljen, bootstrap response koristi `THINGSBOARD_HOST`.

Virtualni uredjaji se mogu ponasati kao ThingsBoard MQTT device client ako je
`VIRTUAL_SIMULATOR_MQTT_RPC_ENABLED=true`. Backend se tada spaja na MQTT broker
sa spremljenim access tokenom svakog virtualnog uredjaja, slusa
`v1/devices/me/rpc/request/+`, lokalno izvrsi RPC i vrati odgovor na
`v1/devices/me/rpc/response/{requestId}`. Podrzane metode su `openWindow`,
`closeWindow`, `setWindowPosition`, `stopWindow`, `openBlinds`, `closeBlinds`,
`setBlindsPosition` i `stopBlinds`; `set*Position` prima `{"position": 0-100}`.
Ako je uz to ukljucen `WINDOWSENSE_COMMANDS_RPC_ENABLED=true`, klik na komandu
u aplikaciji za virtualni uredjaj ide kroz isti ThingsBoard two-way RPC tok:
app -> backend REST RPC -> ThingsBoard -> MQTT -> backend virtual-device listener.
Ako RPC nije konfiguriran, virtualni uredjaj zadrzava lokalni fallback.

Ako ESP vec ima hardkodirani ThingsBoard access token, admin/proizvodni flow moze
unaprijed registrirati uredjaj bez izlaganja tokena korisnickom frontendu:

Za brzi prototip/admin unos dovoljan je samo token:

```http
POST /api/admin/physical-devices/register-token
```

```json
{
  "thingsBoardAccessToken": "roleta1"
}
```

Backend tada sam generira naziv uredjaja, serijski broj i pairing code. Response
vraca pairing code koji korisnik poslije upisuje u sobi; access token se ne
vraca i lokalno se sprema samo njegov SHA-256 hash.

Dodavanje registriranog fizickog uredjaja u ThingsBoard room/entity radi se
odvojenim pozivom:

```http
POST /api/rooms/{roomId}/devices/entity
```

```json
{
  "name": "Roleta 1",
  "pairingCode": "WS-ABCDEFGH"
}
```

Taj endpoint ne kreira novi ThingsBoard device ni novi room/entity. Koristi
postojeci `tbDeviceId` iz registryja i dodaje relation prema postojecem room
assetu.

Detaljni proizvodni endpoint i dalje podrzava rucno zadavanje svih polja:

```http
POST /api/admin/physical-devices/register-token-device
```

```json
{
  "deviceName": "ESP32 - Kuhinja",
  "serialNumber": "WS-ESP32-0001",
  "hardwareId": "ESP32-A1B2C3D4",
  "firmwareVersion": "1.0.0",
  "capabilities": ["window", "blinds", "rain", "lux", "temp", "wind"],
  "pairingCode": "WS-DEMO-0001",
  "thingsBoardAccessToken": "hardcoded-token-on-esp"
}
```

Backend tada kreira ThingsBoard `DEVICE`, postavi tocno taj `ACCESS_TOKEN`,
spremi lokalni registry zapis kao `CLAIMABLE` i vrati samo `tbDeviceId`,
`serialNumber`, `hardwareId` i status. Token se ne sprema u frontend response
niti kao plaintext u lokalnu bazu; baza cuva samo SHA-256 hash tokena kako bi
backend mogao odbiti ponovnu registraciju istog ESP tokena.
Korisnik poslije povezuje uredjaj u sobu standardnim pairing code flowom.

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
