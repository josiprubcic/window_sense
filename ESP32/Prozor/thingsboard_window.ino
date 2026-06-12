#include <WiFi.h>
#include <PubSubClient.h>
#include <ESP32Servo.h>
#include <ArduinoJson.h>
#include <WiFiManager.h>          

// === POSTAVKE THINGSBOARD MQTT BROKERA ===
const char* THINGSBOARD_SERVER = "eu.thingsboard.cloud";
const int THINGSBOARD_PORT = 1883;
const char* THINGSBOARD_ACCESS_TOKEN = "4ptlm2q1p8w5jvhq5i5s";

// === PINOVI ===
const int SERVO_PIN = 3;
const int MAGNET_PIN = 6;
const int LIGHT_PIN = 0;      // fotootpornik (ADC)

// === KUTOVI ===
const int CLOSED_ANGLE = 0;
const int OPEN_ANGLE = 90;

// === POSTAVKE ZA DAN/NOĆ ===
const int DAY_THRESHOLD = 1500;   // Iznad = dan, ispod = noć

// === GLOBALNE VARIJABLE ===
WiFiClient espClient;
PubSubClient client(espClient);
Servo windowServo;

int trenutniKut = 0;
int trenutnoStanje = -1;      // 0 zatvoreno, 1 otvoreno
int trenutnoDan = -1;         // 0 noć, 1 dan

// === SLANJE TELEMETRIJE ===
void sendTelemetry(String key, int value) {
  if (client.connected()) {
    String payload = "{\"" + key + "\":" + value + "}";
    Serial.print("Šaljem telemetriju: ");
    Serial.println(payload);
    client.publish("v1/devices/me/telemetry", payload.c_str());
  } else {
    Serial.println("MQTT nije spojen – telemetrija nije poslana.");
  }
}

// === OBRADA RPC NAREDBI ===
void callback(char* topic, byte* payload, unsigned int length) {
  String message;
  for (unsigned int i = 0; i < length; i++) message += (char)payload[i];
  Serial.print("RPC stigao: "); Serial.println(topic);
  Serial.print("Payload: "); Serial.println(message);

  String requestIdStr = String(topic);
  requestIdStr.replace("v1/devices/me/rpc/request/", "");
  int requestId = requestIdStr.toInt();

  StaticJsonDocument<200> doc;
  DeserializationError error = deserializeJson(doc, message);
  if (error) {
    Serial.print("JSON parse error: ");
    Serial.println(error.c_str());
    return;
  }

  const char* method = doc["method"];
  int value = doc["params"];

  if (strcmp(method, "setAngle") == 0) {
    int kut = constrain(value, 0, 90);
    Serial.printf("RPC setAngle: pomičem servo na %d°\n", kut);
    windowServo.write(kut);
    trenutniKut = kut;
    delay(300);
    String responseTopic = "v1/devices/me/rpc/response/" + String(requestId);
    client.publish(responseTopic.c_str(), "{\"success\":true}");
    Serial.println("Odgovor poslan.");
  } 
  else if (strcmp(method, "getState") == 0) {
    int currentAngle = windowServo.read();
    String responseTopic = "v1/devices/me/rpc/response/" + String(requestId);
    String responsePayload = "{\"value\":" + String(currentAngle) + "}";
    client.publish(responseTopic.c_str(), responsePayload.c_str());
    Serial.printf("RPC getState: vraćam %d°\n", currentAngle);
  } 
  else {
    Serial.println("Nepoznata RPC metoda: " + String(method));
    String responseTopic = "v1/devices/me/rpc/response/" + String(requestId);
    client.publish(responseTopic.c_str(), "{\"error\":\"unknown method\"}");
  }
}

// === MQTT POVEZIVANJE ===
void reconnectMQTT() {
  while (!client.connected()) {
    Serial.print("Povezivanje na ThingsBoard MQTT...");
    if (client.connect("ESP32_Client", THINGSBOARD_ACCESS_TOKEN, NULL)) {
      Serial.println(" Spojeno!");
      client.subscribe("v1/devices/me/rpc/request/+");
      Serial.println("Pretplaćeno na RPC zahtjeve.");
    } else {
      Serial.print(" Neuspješno, rc=");
      Serial.print(client.state());
      Serial.println(" Ponovno za 5 sek.");
      delay(5000);
    }
  }
}

// === SETUP ===
void setup() {
  Serial.begin(115200);
  delay(100);
  Serial.println("\n=== ESP32 START ===");

  
  WiFiManager wm;
  // Generiraj jedinstveno ime AP-a (npr. "SmartWindow")
  String apName = "SmartWindow";
  Serial.print("Ako se ne spoji, pristupna točka: ");
  Serial.println(apName);

  // autoConnect(apName, password) 
  bool connected = wm.autoConnect(apName.c_str());
  
  if (!connected) {
    Serial.println("WiFiManager: nije uspjelo spajanje, resetiram...");
    delay(3000);
    ESP.restart();
  }
  Serial.println("WiFi spojen!");
  Serial.print("IP adresa: ");
  Serial.println(WiFi.localIP());
  Serial.print("SSID: ");
  Serial.println(WiFi.SSID());

  // ---- Postavke MQTT ----
  client.setServer(THINGSBOARD_SERVER, THINGSBOARD_PORT);
  client.setCallback(callback);

  // ---- Pinovi i servo ----
  pinMode(MAGNET_PIN, INPUT_PULLUP);
  pinMode(LIGHT_PIN, INPUT);
  windowServo.attach(SERVO_PIN);
  windowServo.write(CLOSED_ANGLE);
  trenutniKut = CLOSED_ANGLE;
  Serial.println("Servo postavljen na 0° (zatvoreno).");
}

// === GLAVNA PETLJA ===
void loop() {
  if (!client.connected()) {
    reconnectMQTT();
  }
  client.loop();

  // --- FOTOTRANZISTOR (dan/noć) ---
  int lightValue = analogRead(LIGHT_PIN);
  bool jeDan = (lightValue > DAY_THRESHOLD);
  int danStatus = jeDan ? 1 : 0;

  if (danStatus != trenutnoDan) {
    sendTelemetry("day", danStatus);
    trenutnoDan = danStatus;
    Serial.printf("Svjetlost: %d -> %s\n", lightValue, jeDan ? "DAN" : "NOĆ");
  }

  // --- MAGNETNI SENZOR (prozor zatvoren/otvoren) ---
  int magnetState = digitalRead(MAGNET_PIN);
  bool zatvoren = (magnetState == LOW);
  if (zatvoren && trenutnoStanje != 0) {
    sendTelemetry("windowStatus", 0);
    trenutnoStanje = 0;
    Serial.println("Fizičko stanje: ZATVORENO.");
  } else if (!zatvoren && trenutnoStanje != 1) {
    sendTelemetry("windowStatus", 1);
    trenutnoStanje = 1;
    Serial.println("Fizičko stanje: OTVORENO.");
  }

  // Ispis svakih 5 sekundi za praćenje
  static unsigned long lastPrint = 0;
  if (millis() - lastPrint > 5000) {
    Serial.printf("Magnet: %s | Servo kut: %d° | Light ADC: %d\n", 
                  zatvoren ? "ZATVORENO" : "OTVORENO", 
                  windowServo.read() + 1, lightValue);
    lastPrint = millis();
  }
  delay(100);
}