# windows_sense

Web aplikacija za pametni sustav automatskog upravljanja prozorima i roletama prema vremenskim uvjetima.

## Tehnologije

- Java 21
- Spring Boot 3
- Maven
- HTML, CSS i JavaScript frontend
- ThingsBoard HTTP Device API priprema

## Pokretanje

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
export THINGSBOARD_HOST=https://thingsboard.cloud
export THINGSBOARD_ACCESS_TOKEN=DEVICE_ACCESS_TOKEN
export THINGSBOARD_SYNC_ENABLED=true
export WINDOWSENSE_DEVICE_ID=windowsense-esp32-01
mvn spring-boot:run
```
