/**
 *  Turn on Sprinklers Unless There's Rain - Sprinkler Timer 2
 *
 *  Copyright 2014 Matthew Nichols
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *
 * NOTE: This app is using experimental precipitation measurement from weather feature to determine if watering should occur.
 *
**/

definition(
    name: "Turn on Sprinklers Unless There's Rain",
    namespace: "d8adrvn/smart_sprinkler",
    author: "matt@nichols.name",
    description: "Schedule sprinklers run every n days unless rain is in the forecast.",
	category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/water_moisture.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/water_moisture@2x.png"
)

preferences {
	page(name: "schedulePage", title: "Schedule", nextPage: "sprinklerPage", uninstall: true) {
		section("Water every...") {
			input "days", "number", title: "Days?", required: true
		}
		section("Water at...") {
			input "time", "time", title: "When?", required: true
		}
    	section("Use this virtual scheduler device...") {
    		input "schedulerVirtualDevice", "capability.actuator", required: false
    	}
    }
    page(name: "sprinklerPage", title: "Sprinkler Controller Setup", nextPage: "weatherPage", install: true) {
		section("Sprinkler switches...") {
			input "switches", "capability.switch", multiple: true
		}
        section("Zone Times") {
			input "zone1", "string", title: "zone 1", multiple: false, required: false
			input "zone2", "string", title: "zone 2", multiple: false, required: false
			input "zone3", "string", title: "zone 3", multiple: false, required: false
			input "zone4", "string", title: "zone 4", multiple: false, required: false
			input "zone5", "string", title: "zone 5", multiple: false, required: false
			input "zone6", "string", title: "zone 6", multiple: false, required: false
			input "zone7", "string", title: "zone 7", multiple: false, required: false
			input "zone8", "string", title: "zone 8", multiple: false, required: false
		}
    }
    /*
    page(name: "weatherPage", title: "Weather Settings", install: true) {
		section("Zip code to check weather...") {
			input "zipcode", "text", title: "Zipcode?", required: false
		}
    	section("Skip watering if more than... (default 0.5)") {
			input "wetThreshold", "number", title: "Inches?", required: false
		}
    }
    */
}

def installed() {
	log.info "Installed: $settings"
	
	subscribe(switches, "switch.on", sprinklersOn)
	schedule(time, "scheduleCheck")
}

def updated() {
	log.info "Updated: $settings"
	
	unsubscribe()
	unschedule()
	
	subscribe(switches, "switch.on", sprinklersOn)
	schedule(time, "scheduleCheck")
}

def sprinklersOn(evt) {
	if(!state.triggered && daysSince() > 0) {
		sendPush("Looks like the sprinklers are on. Pushing back next watering day.")
		state.daysSinceLastWatering = 0
	}
	state.triggered = false
}

def scheduleCheck() {
	log.debug("Schedule check")
    
    def schedulerState = "noEffect"
    if (schedulerVirtualDevice) {
    	schedulerState = schedulerVirtualDevice.latestValue("effect")
    }
    
    if (schedulerState == "onHold") {
		log.info("Sprinkler schedule on hold.")
    	return
    } else {
        schedulerVirtualDevice?.noEffect()
	}

    def inches = todaysPercip()
	if (schedulerState != "delay" && inches < (wetThreshold?.toFloat() ?: 0.5)) {
		state.daysSinceLastWatering = daysSince() + 1
    }
	log.info("Checking sprinkler schedule. ${daysSince()} days since laste watering. ${inches} inches of percip today. Threshold: ${wetThreshold?.toFloat() ?: 0.5}")
    
	if (daysSince() >= days || schedulerState == "expedite") {
		sendPush("Watering now!")
        water()
        state.daysSinceLastWatering = 0
    }
    // Assuming that sprinklers will turn themselves off. Should add a delayed off?
}


def turnOnZone(z) {
	def zoneTime = settings["zone${z}"]
    if(zoneTime) {
    	log.info("Zone ${z} on for ${zoneTime}")
    	switches."RelayOn${z}For"(zoneTime)
    }
}

def water() {
	state.triggered = true
    if(anyTimes()) {
    	turnOnZone(1)
    	turnOnZone(2)
    	turnOnZone(3)
    	turnOnZone(4)
    	turnOnZone(5)
    	turnOnZone(6)
    	turnOnZone(7)
    	turnOnZone(8)
        
    } else {
    	log.debug("Turning all zones on")
    	switches.on()
	}
}

def anyTimes() {
	return zone1 || zone2 || zone3 || zone4 || zone5 || zone6 || zone7 || zone8
}

def todaysPercip() {
	def result = 0.0
    if (zipcode) {
		def weather = getWeatherFeature("conditions", zipcode)
    	result = weather.current_observation.precip_today_in.toFloat()
        log.info("Checking percipitation for $zipcode: $result in")
    }
    result
}

def daysSince()
{
	if(!state.daysSinceLastWatering) state.daysSinceLastWatering = 0
    state.daysSinceLastWatering
}
