#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <ESP32Servo.h>
#include <WiFiManager.h>

const char* mqttServer = "eu.thingsboard.cloud";
const char* token = "roleta1";

const char* rpcTopic = "v1/devices/me/rpc/request/+";
const char* telemetryTopic = "v1/devices/me/telemetry";

Servo servo;

const int servoPin = 33;

const int minPulseWidth = 500;
const int maxPulseWidth = 2500;

const int stopPulse = 1500;

const int FULL_TRAVEL_TIME = 3000;

int currentPosition = 100;

WiFiClient espClient;
PubSubClient client(espClient);

void moveToPosition(int targetPosition) {

  targetPosition = constrain(targetPosition, 0, 100);

  int delta = targetPosition - currentPosition;

  if (delta == 0) return;

  int speed = (delta > 0) ? 50 : -50;

  int percent = abs(delta);
  int travelTime = (FULL_TRAVEL_TIME * percent) / 100;

  int servoValue = map(speed, -100, 100, 0, 180);

  int pulseWidth = map(
      servoValue,
      0,
      180,
      minPulseWidth,
      maxPulseWidth
  );

  servo.writeMicroseconds(pulseWidth);

  Serial.print("Pomakni:");
  Serial.print(currentPosition);
  Serial.print("% -> ");
  Serial.print(targetPosition);
  Serial.print("% | ");
  Serial.print(travelTime);
  Serial.println("ms");

  delay(travelTime);

  servo.writeMicroseconds(stopPulse);

  currentPosition = targetPosition;

  String payload = "{\"position\":";
  payload += currentPosition;
  payload += "}";

  client.publish(telemetryTopic, payload.c_str());

  Serial.println("Telemetrija poslana.");
}

void callback(char* topic, byte* payload, unsigned int length) {

  String msg = "";

  for (unsigned int i = 0; i < length; i++) {
    msg += (char)payload[i];
  }

  Serial.println("RPC: " + msg);

  if (msg.indexOf("\"method\":\"getState\"") != -1) {

    String response = "{\"position\":";
    response += currentPosition;
    response += "}";

    client.publish(telemetryTopic, response.c_str());

    Serial.print("Stanje poslano: ");
    Serial.println(currentPosition);

    return;
  }

  if (msg.indexOf("\"method\":\"setAngle\"") != -1) {

    int paramsIndex = msg.indexOf("\"params\":");

    if (paramsIndex == -1) {
      Serial.println("Nije pronadjen params");
      return;
    }

    String valueStr = msg.substring(paramsIndex + 9);
    valueStr.trim();

    int endIndex = valueStr.indexOf('}');
    if (endIndex != -1) {
      valueStr = valueStr.substring(0, endIndex);
    }

    valueStr.trim();

    int pos = valueStr.toInt();

    Serial.print("Parsirana pozicija: ");
    Serial.println(pos);

    if (pos >= 0 && pos <= 100) {
      moveToPosition(pos);
    } else {
      Serial.println("Nevaljanja pozicija");
    }

    return;
  }

  Serial.println("Nepoznata RPC poruka");
}
void reconnect() {

  while (!client.connected()) {

    Serial.print("Spajanje na ThingsBoard...");

    if (client.connect("ESP32_Roleta", token, NULL)) {

      Serial.println("spojeno!");

      client.subscribe(rpcTopic);

    } else {

      Serial.print("neuspjelo, rc=");
      Serial.println(client.state());

      delay(2000);
    }
  }
}

void setup() {

  Serial.begin(115200);

  servo.setPeriodHertz(50);

  servo.attach(
      servoPin,
      minPulseWidth,
      maxPulseWidth
  );

  servo.writeMicroseconds(stopPulse);

  WiFiManager wm;

  String apName = "SmartBlind";

  bool res = wm.autoConnect(apName.c_str());

  if (!res) {
    Serial.println("Neuspjelo spajanje");
  } else {
    Serial.println("Spojeno!");
    Serial.println(WiFi.localIP());
    Serial.print("SSID: ");
Serial.println(WiFi.SSID());
  }

  client.setServer(mqttServer, 1883);
  client.setCallback(callback);

  Serial.println("THINGSBOARD ROLETNA SPREMNA");
}

void loop() {

  if (!client.connected()) {
    reconnect();
  }

  client.loop();
}