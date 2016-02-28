/**
 *  MQTT Bridge
 *
 *  Authors
 *   - st.john.johnson@gmail.com
 *   - jeremiah.wuenschel@gmail.com
 *
 *  Copyright 2016
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

definition(
    name: "MQTT Bridge",
    namespace: "stj",
    author: "St. John Johnson and Jeremiah Wuenschel",
    description: "A bridge between SmartThings and MQTT",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Connections/Cat-Connections@3x.png"
)

preferences {
    section("Send Notifications?") {
        input("recipients", "contact", title: "Send notifications to", multiple: true, required: false)
    }

    section ("Input") {
        input "accelerationSensors", "capability.accelerationSensor", title: "Acceleration Sensors", multiple: true, required: false
        input "switches", "capability.switch", title: "Switches", multiple: true, required: false
        input "levels", "capability.switchLevel", title: "Levels", multiple: true, required: false
        input "powerMeters", "capability.powerMeter", title: "Power Meters", multiple: true, required: false
        input "motionSensors", "capability.motionSensor", title: "Motion Sensors", multiple: true, required: false
        input "contactSensors", "capability.contactSensor", title: "Contact Sensors", multiple: true, required: false
        input "temperatureSensors", "capability.temperatureMeasurement", title: "Temperature Sensors", multiple: true, required: false
        input "humiditySensors", "capability.relativeHumidityMeasurement", title: "Humidity Sensors", multiple: true, required: false
        input "waterSensors", "capability.waterSensor", title: "Water Sensors", multiple: true, required: false
        input "windowShades", "capability.windowShade", title: "Window Shades/Blinds", multiple: true, required: false
        input "presenceSensors", "capability.presenceSensor", title: "Presence Sensors", multiple: true, required: false
        input "garageDoors", "capability.garageDoorControl", title: "Garage Doors", multiple: true, required: false
    }

    section ("Bridge") {
        input "bridge", "capability.notification", title: "Notify this Bridge", required: true, multiple: false
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"

    runEvery15Minutes(initialize)
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    // Unsubscribe from all events
    unsubscribe()
    // Subscribe to stuff
    initialize()
}

// Return list of displayNames
def getDeviceNames(devices) {
    def list = []
    devices.each{device->
        list.push(device.displayName)
    }
    list
}

def initialize() {
    // Subscribe to new events from devices
    subscribe(accelerationSensors, "acceleration", inputHandler)
    subscribe(powerMeters, "power", inputHandler)
    subscribe(motionSensors, "motion", inputHandler)
    subscribe(switches, "switch", inputHandler)
    subscribe(levels, "level", inputHandler)
    subscribe(contactSensors, "contact", inputHandler)
    subscribe(temperatureSensors, "temperature", inputHandler)
    subscribe(humiditySensors, "humidity", inputHandler)
    subscribe(waterSensors, "water", inputHandler)
    subscribe(windowShades, "windowShade", inputHandler)
    subscribe(presenceSensor, "presence", inputHandler)
    subscribe(garageDoors, "door", inputHandler)

    subscribe(location, "alarmSystemStatus", alarmInputHandler)

    // Subscribe to events from the bridge
    subscribe(bridge, "message", bridgeHandler)

    // Update the bridge
    updateSubscription()
}

// Update the bridge's subscription
def updateSubscription() {
    def json = new groovy.json.JsonOutput().toJson([
        path: '/subscribe',
        body: [
            devices: [
                acceleration: getDeviceNames(accelerationSensors),
                power: getDeviceNames(powerMeters),
                motion: getDeviceNames(motionSensors),
                switch: getDeviceNames(switches),
                level: getDeviceNames(levels),
                contact: getDeviceNames(contactSensors),
                temperature: getDeviceNames(temperatureSensors),
                humidity: getDeviceNames(humiditySensors),
                water: getDeviceNames(waterSensors),
                windowShade: getDeviceNames(windowShades),
                presence: getDeviceNames(presenceSensors),
                door: getDeviceNames(garageDoors),
                notify: ["Contacts", "System"],
                setAlarm: ["alarm system status"]
            ]
        ]
    ])

    log.debug "Updating subscription: ${json}"

    bridge.deviceNotification(json)
}

// Receive an event from the bridge
def bridgeHandler(evt) {
    def json = new JsonSlurper().parseText(evt.value)

    switch (json.type) {
        case "acceleration":
        case "power":
        case "contact":
        case "temperature":
        case "humidity":
        case "water":
        case "motion":
        case "presence":
            // Do nothing, we can change nothing here
            break
        case "notify":
          if (json.name == "Contacts") {
              sendNotificationToContacts("${json.value}", recipients)
          } else {
              sendNotificationEvent("${json.value}")
          }
          break
        case "setAlarm":
            sendLocationEvent(name: "alarmSystemStatus", value: json.value)
        case "switch":
            switches.each{device->
                if (device.displayName == json.name) {
                    if (json.value == 'on') {
                        device.on();
                    } else {
                        device.off();
                    }
                }
            }
            break
        case "windowShade":
            windowShades.each{device->
                if (device.displayName == json.name) {
                    if (json.value == 'open') {
                        device.open()
                    } else {
                        device.close()
                    }
                }
            }
            break
        case "door":
            garageDoors.each{device->
                if (device.displayName == json.name) {
                    if (json.value == 'open') {
                        device.open()
                    } else {
                        device.close()
                    }
                }
            }
            break
        case "level":
            levels.each{device->
                if (device.displayName == json.name) {
                    device.setLevel(json.value);
                }
            }
            break
      default:
        break
    }

    log.debug "Receiving device event from bridge: ${json}"
}

// Receive an event from a device
def inputHandler(evt) {
    def body = [
            name: evt.displayName,
            value: evt.value,
            type: evt.name
        ]
    sendEventBody(body)
}

def alarmInputHandler(evt) {
    def body = [
        name: evt.displayName,
        value: evt.value,
        type: evt.name
    ]
    switch (evt.value) {
        case "off":
            body["value"] = "disarmed"
            break
        case "stay":
            body["value"] = "armed_home"
            break
        case "away":
            body["value"] = "armed_away"
            break
        default:
            log.info "need to fix up ${evt.value}"
            break
    }
    sendEventBody(body)
}

def sendEventBody(body) {
    def json = new JsonOutput().toJson([
        path: '/push',
        body: body
    ])

    log.debug "Forwarding device event to bridge: ${json}"
    bridge.deviceNotification(json)
}

