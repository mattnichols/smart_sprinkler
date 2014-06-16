/**
 *  Sprinkler Timer
 *
 *  Copyright 2014 Matthew Nichols & Stand Dotson
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
    name: "Sprinkler Timer",
    namespace: "d8adrvn/smart_sprinkler",
    author: "matt@nichols.name",
    description: "Schedule sprinklers run unless rain is rain.",
	category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/water_moisture.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/water_moisture@2x.png"
)

preferences {
	page(name: "schedulePage", title: "Schedule", nextPage: "sprinklerPage", uninstall: true) {
		section("Water every...") {
			input "days", "number", title: "Days?", required: false
		}
		
		section {
			input (
				name: "wateringDays", 
				type: "enum", 
				title: "Which days of the week?", 
				required: false,
				multiple: true, 
				metadata: [values: ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']])
			//log.debug "days to run are: ${wateringDays}"
		}
		
		section("Select times to turn them on...") {
			input name: "waterTimeOne",  type: "time", required: true, title: "Turn them all on at..."
			input name: "waterTimeTwo",  type: "time", required: false, title: "and again at..."
			input name: "waterTimeThree",  type: "time", required: false, title: "and again at..."
		}
		
		section("Use this virtual scheduler device...") {
			input "schedulerVirtualDevice", "capability.actuator", required: false
		}
	}
	page(name: "sprinklerPage", title: "Sprinkler Controller Setup", install: true) {
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
	log.debug "Installed: $settings [$wateringDays]"
    scheduling()
    schedule(waterTime, scheduleCheck)
}

def updated() {
	log.debug "Updated: $settings [$wateringDays]"
	unschedule()
    scheduling()

}

// Scheduling
def scheduling() {
    schedule(waterTimeOne, "waterTimeOneStart")
	if (waterTimeTwo) {
    	schedule(waterTimeTwo, "waterTimeTwoStart")
   	}
	if (waterTimeThree) {
    	schedule(waterTimeThree, "waterTimeThreeStart")
   	}
}

def waterTimeOneStart() {
	scheduleCheck()
}
def waterTimeTwoStart() {
	scheduleCheck()
}
def waterTimeThreeStart() {
	scheduleCheck()
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
	
	// Change to rain delay if wet
	schedulerState = isRainDelay() ? "delay" : schedulerState
	
	if (schedulerState != "delay") {
		state.daysSinceLastWatering = daysSince() + 1
	}
    
	log.debug("Schedule effect $schedulerState. Days since last watering ${daysSince()}. Is watering day? ${isWateringDay()}. Enought time? ${enoughTimeElapsed(schedulerState)} ")
    
	if ((isWateringDay() && enoughTimeElapsed(schedulerState) && schedulerState != "delay") || schedulerState == "expedite") {
		sendPush("Watering now!")
		state.daysSinceLastWatering = 0
		water()
		// Assuming that sprinklers will turn themselves off. Should add a delayed off?
	}
}

def enoughTimeElapsed(schedulerState) {
	if(!days) return true
	return (daysSince() >= days)
}

def isRainDelay() { 
	wasWetYesterday() || isWet() || isStormy()
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
	if(anyZoneTimes()) {
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

def anyZoneTimes() {
	return zone1 || zone2 || zone3 || zone4 || zone5 || zone6 || zone7 || zone8
}

def isWateringDay() {
	if(!wateringDays || wateringDays?.empty()) return true
	
	def today = new Date().format("EEEE", location.timeZone)
	log.debug "today: ${today}, days: ${days}"
	if (wateringDays.contains(today)) {
		return true
	}
	log.trace "watering is not scheduled for today"
	return false
}

def wasWetYesterday() {
	if (!zipcode) return false
	
 	def yesterdaysWeather = getWeatherFeature("yesterday", zipcode)
    def yesterdaysPrecip=yesterdaysWeather.history.dailysummary.precipi.toArray()
   	def yesterdaysInches=yesterdaysPrecip[0].toFloat()
    log.info("Checking yesterdays percipitation for $zipcode: $yesterdaysInches in")
    
    if (yesterdaysInches > wetThreshold.toFloat()) {
        return true  // rainGuage is full
    }
 	else {
        return false // rainGuage is empty or below the line
    }
}

def isWet() {
	if (!zipcode) return false
	
	def todaysWeather = getWeatherFeature("conditions", zipcode)
   	def todaysInches = todaysWeather.current_observation.precip_today_in.toFloat()
	log.info("Checking percipitation for $zipcode: $todaysInches in")
	if (todaysInches > wetThreshold.toFloat()) {
  		return true  // rain gauge is full
	}
	else {
		return false
	}
}

def isStormy() {
	if (!zipcode) return false
	
	def forecastWeather = getWeatherFeature("forecast", zipcode)
	def forecastPrecip=forecastWeather.forecast.simpleforecast.forecastday.qpf_allday.in.toArray()
	def forecastInches=forecastPrecip[0].toFloat()
	log.info("Checking forecast percipitation for $zipcode: $forecastInches in")
	if (forecastInches > wetThreshold.toFloat()) {
		return true  // rain guage is forecasted to be full
	}
	else {
		return false
	}
}

def daysSince() {
	if(!state.daysSinceLastWatering) state.daysSinceLastWatering = 0
	state.daysSinceLastWatering
}
