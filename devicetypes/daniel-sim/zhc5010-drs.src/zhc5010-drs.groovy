/**
 *  ZHC5010 DRS
 *
 *  Copyright 2017 Daniel Sim
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
 */
metadata {
	// http://logichome.dk/index.php/10-support-categories/5-zhc5010-technical-information
	definition (name: "ZHC5010 DRS", namespace: "daniel-sim", author: "Daniel Sim") {
    	capability "Actuator"
    	capability "Button"
        capability "Switch"
        capability "Polling"
        capability "Refresh"
	}

	tiles {
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
            state "on", label: '${name}', action: "switch.off",
                icon: "st.switches.switch.on", backgroundColor: "#79b821"
            state "off", label: '${name}', action: "switch.on",
                icon: "st.switches.switch.off", backgroundColor: "#ffffff"
        }
        standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        main "switch"
		details(["switch","refresh"])
	}
    
    preferences {
    	section {
            input (
                type: "paragraph",
                element: "paragraph",
                title: "DEVICE PARAMETERS",
                description: "Configuration parameters supported by the physical device."
            )

            input (
                name: "configRelayMode",
                title: "This parameter configures which of the buttons that shall control the built-in relay, or if the relay only will be activated for one second, each time button #1 is used.",
                type: "enum",
                options: [
                    "0" : "Relay is disabled",
                    "1" : "Relay is controlled by button #1 or by upper paddle when pair mode is active (Default)",
                    "2" : "Relay is controlled by button #2 or by upper paddle when pair mode is active",
                    "3" : "Relay is controlled by button #3 or by upper paddle when pair mode is active",
                    "4" : "Relay is controlled by button #4 or by upper paddle when pair mode is active",
                    "5" : "Relay is activated for half a second and is controlled by button #1 or by upper paddle when pair mode is active",
                    "6" : "Relay is activated for half a second and is controlled by button #2 or by upper paddle when pair mode is active",
                    "7" : "Relay is activated for half a second and is controlled by button #3 or by lower paddle when pair mode is active",
                    "8" : "Relay is activated for half a second and is controlled by button #4 or by lower paddle when pair mode is active",
                    "9" : "Relay follows the state of button #1; when button is down the relay is on and when button is released the relay is off. Incoming button command messages will result in a short activation of the relay",
                    "10" : "Relay follows the state of button #2; when button is down the relay is on and when button is released the relay is off. Incoming button command messages will result in a short activation of the relay",
                    "11" : "Relay follows the state of button #3; when button is down the relay is on and when button is released the relay is off. Incoming button command messages will result in a short activation of the relay",
                    "12" : "Relay follows the state of button #4; when button is down the relay is on and when button is released the relay is off. Incoming button command messages will result in a short activation of the relay",
                    "13" : "Relay is only controlled by commands sent to the root device. Commands to the root device will not be forwarded to device 1",
                ],
                defaultValue: "1",
                required: true
            )
        }
    }
}

def updated() {
	log.trace "updated():"

	def cmds = []

	if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 2000) {
		state.updatedLastRanAt = now()

        log.debug "saving... configRelayMode: ${configRelayMode}"
        cmds << zwave.configurationV1.configurationSet(parameterNumber: 15, size: 1, scaledConfigurationValue: configRelayMode.toInteger())
    	cmds << zwave.configurationV1.configurationGet(parameterNumber: 15)
		
		return sendCommands(cmds)
	}
	else {
		log.trace "updated(): Ran within last 2 seconds so aborting."
	}
}

/**
 *  sendCommands(cmds, delay=200)
 *
 *  Sends a list of commands directly to the device using sendHubCommand.
 *  Uses encapCommand() to apply security or CRC16 encapsulation as needed.
 **/
private sendCommands(cmds, delay=200) {
log.debug "sendHubCommand"
    sendHubCommand( cmds.collect{ (it instanceof physicalgraph.zwave.Command ) ? response(encapCommand(it)) : response(it) }, delay)
}
/**
 *  encapCommand(cmd)
 *
 *  Applies security encapsulation to a command.
 *  Returns a physicalgraph.zwave.Command.
 **/
private encapCommand(physicalgraph.zwave.Command cmd) {
    log.debug "encapCommand"
    return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd)
}

/**
 *  zwaveEvent( COMMAND_CLASS_CONFIGURATION (0x70) : CONFIGURATION_REPORT (0x03) )
 *
 *  The Configuration Report Command is used to advertise the actual value of the advertised parameter.
 *
 *  Action: Store the value in the parameter cache, update syncPending, and log an info message.
 *   Update wheelStatus if parameter #2.
 *
 *  Note: Ideally, we want to update the corresponding preference value shown on the Settings GUI, however this
 *  is not possible due to security restrictions in the SmartThings platform.
 *
 *  cmd attributes:
 *    List<Short>  configurationValue  Value of parameter (byte array).
 *    Short        parameterNumber     Parameter ID.
 *    Short        size                Size of parameter's value (bytes).
 *
 *  Example: ConfigurationReport(configurationValue: [10], parameterNumber: 0, reserved11: 0,
 *            scaledConfigurationValue: 10, size: 1)
 **/
def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
    log.debug "zwaveEvent(): v2 Configuration Report received: ${cmd}"
}


// parse events into attributes
def parse(String description) {
	log.debug('parse')
	def result = null
    def cmd = zwave.parse(description)
    if (cmd) {
        result = zwaveEvent(cmd)
        log.debug "Parsed ${cmd} to ${result.inspect()}"
    } else {
        log.debug "Non-parsed event: ${description}"
    }
    return result
}

/**
 *  zwaveEvent( COMMAND_CLASS_SECURITY (0x98) : SECURITY_MESSAGE_ENCAPSULATION (0x81) )
 *
 *  The Security Message Encapsulation command is used to encapsulate Z-Wave commands using AES-128.
 *
 *  Action: Extract the encapsulated command and pass to the appropriate zwaveEvent() handler.
 *    Set state.useSecurity flag to true.
 *
 *  cmd attributes:
 *    List<Short> commandByte         Parameters of the encapsulated command.
 *    Short   commandClassIdentifier  Command Class ID of the encapsulated command.
 *    Short   commandIdentifier       Command ID of the encapsulated command.
 *    Boolean secondFrame             Indicates if first or second frame.
 *    Short   sequenceCounter
 *    Boolean sequenced               True if the command is transmitted using multiple frames.
 **/
def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    log.debug "zwaveEvent(): Security Encapsulated Command received: ${cmd}"

    state.useSecurity = true

    def encapsulatedCommand = cmd.encapsulatedCommand(getCommandClassVersions())
    if (encapsulatedCommand) {
        return zwaveEvent(encapsulatedCommand)
    } else {
        logger("zwaveEvent(): Unable to extract security encapsulated command from: ${cmd}","error")
    }
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd)
{
   	log.debug "basic report"
   	def result = []
    result << createEvent(name:"switch", value: cmd.value ? "on" : "off")

    result
}

def zwaveEvent(physicalgraph.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
	log.debug("sceneNumber: ${cmd.sceneNumber} keyAttributes: ${cmd.keyAttributes}")
    
    // Button
    // sceneNumber	: 	1-4		buttons
    // keyAttributes: 	0		pushed
    //					1		released (first held)
    //					2		held (then released)	
    //					3		double-pushed
    def action = null
    if (cmd.keyAttributes == 0) {
    	action = "pushed"
    } else if (cmd.keyAttributes == 1) {
    	action = "released"
    } else if (cmd.keyAttributes == 2) {
    	action = "held"
    } else {
    	log.debug("NOT IMPLEMENTED keyAttributes: ${cmd.keyAttributes}")
    }
    
    if (action != null) {
    	return createEvent([name: "button", value: action, data: [buttonNumber: "$cmd.sceneNumber"], 
                descriptionText: "$device.displayName $cmd.sceneNumber $action", isStateChange: true, type: "physical"])
    }
}

def on() {
	log.debug "ON"
    delayBetween([
    	zwave.basicV1.basicSet(value: 0xFF).format(),
        zwave.basicV1.basicGet().format(),
    ], 100)
}
def off() {
	log.debug "OFF"
    delayBetween([
    	zwave.basicV1.basicSet(value: 0x00).format(),
        zwave.basicV1.basicGet().format(),
    ], 100)
}
def refresh() {
	log.debug "REFRESH"
    delayBetween([
        zwave.basicV1.basicGet().format(),
    ], 100)
}
def poll() {
	log.debug "POLL"
    delayBetween([
        zwave.basicV1.basicGet().format(),
    ], 100)
}

/**
 *  getCommandClassVersions()
 *
 *  Returns a map of the command class versions supported by the device. Used by parse() and zwaveEvent() to
 *  extract encapsulated commands from MultiChannelCmdEncap, MultiInstanceCmdEncap, SecurityMessageEncapsulation,
 *  and Crc16Encap messages.
 **/
private getCommandClassVersions() {
    return [
        0x20: 1, // Basic V1
        0x25: 1, // Switch Binary V1
        0x26: 2, // Switch Multilvel V2
        0x27: 1, // Switch All V1
        0x2B: 1, // Scene Activation V1
        0x30: 2, // Sensor Binary V2
        0x31: 5, // Sensor Multilevel V5
        0x32: 3, // Meter V3
        0x33: 3, // Switch Color V3
        0x56: 1, // CRC16 Encapsulation V1
        0x59: 1, // Association Grp Info
        0x60: 3, // Multi Channel V3
        0x62: 1, // Door Lock V1
        0x70: 2, // Configuration V2
        0x71: 1, // Alarm (Notification) V1
        0x72: 2, // Manufacturer Specific V2
        0x73: 1, // Powerlevel V1
        0x75: 2, // Protection V2
        0x76: 1, // Lock V1
        0x84: 1, // Wake Up V1
        0x85: 2, // Association V2
        0x86: 1, // Version V1
        0x8E: 2, // Multi Channel Association V2
        0x87: 1, // Indicator V1
        0x98: 1  // Security V1
   ]
}