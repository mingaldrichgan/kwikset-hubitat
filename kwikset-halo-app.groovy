/*
    Copyright 2022 Ming Aldrich-Gan.
    Thanks to https://pypi.org/project/aiokwikset/ for reverse-engineering the Kwikset API.
*/
import com.hubitat.app.DeviceWrapper
import groovy.json.JsonOutput
import groovyx.net.http.HttpResponseException

definition(
    name: "Kwikset Halo",
    namespace: "mingaldrichgan",
    author: "Ming Aldrich-Gan",
    description: "Provides control of Kwikset Halo locks",
    category: "",
    iconUrl: "",
    iconX2Url: "",
    importUrl: "https://raw.githubusercontent.com/mingaldrichgan/kwikset-hubitat/main/kwikset-halo-app.groovy"
)

preferences {
    page(uninstall: true, install: true) {
        section(hideable: true, hidden: true, "PRE-REQUISITES") {
            paragraph "<ul>" +
                "<li>You need a computer with Python installed, and know how to run terminal commands.</li>" +
                "<li>You need to have the " +
                    '<a href="https://www.kwikset.com/smart-locks/app" target="_blank">Kwikset mobile app</a>' +
                    "with at least one home added to it.</li>" +
                "<li>Your Kwikset account needs to be associated with a phone number capable of receiving text messages " +
                    "(to get the verification code during setup).</li>" +
            "</ul>"
        }
        section(hideable: true, "IN YOUR TERMINAL") {
            paragraph "<ul>" +
                "<li>Run <kbd>pip install aiokwikset</kbd>.</li>" +
                "<li>Download " +
                    '<a href="https://raw.githubusercontent.com/mingaldrichgan/kwikset-hubitat/main/kwikset-halo-setup.py" target="_blank">' +
                    "<kbd>kwikset-halo-setup.py</kbd></a>and <kbd>cd</kbd> to the download directory.</li>" +
                "<li>Run <kbd>python kwikset-halo-setup.py</kbd> and follow the prompts to get your Home ID and Refresh Token.</li>" +
            "</ul>"
        }
        section {
            input "homeId", "text", title: "Home ID", required: true
            input "refreshToken", "textarea", title: "Refresh Token", required: true, rows: 15
            input "refreshIntervalInSeconds", "number", title: "Refresh Interval (seconds), default: 60", required: true, defaultValue: 60, width: 4
            input "refreshDelayAfterLockOrUnlock", "number", title: "Refresh Delay After Lock/Unlock (seconds), default: 10", required: true, defaultValue: 10, width: 4
            input "refreshSecondsBeforeTokenExpiration", "number", title: "Refresh Seconds Before Token Expiration, default: 60", required: true, defaultValue: 60, width: 4
            input "retryIntervalInSeconds", "number", title: "Retry Interval (seconds), default: 30", required: true, defaultValue: 60, width: 4
            input "isDebugLoggingEnabled", "bool", title: "Enable Debug Logging", defaultValue: false, width: 4
            input "isEventLoggingEnabled", "bool", title: "Enable Event Logging", defaultValue: true, width: 4
        }
    }
}

void installed() {
    refreshTokenAuth(true)
    getKwiksetDevices(true).each createChildDevice
    refreshDevices()
}

void updated() {
    Map[] kwiksetDevices = getKwiksetDevices(true)
    String[] kwiksetDeviceIds = kwiksetDevices*.deviceid
    logDebug "Kwikset device IDs: ${kwiksetDeviceIds}"

    DeviceWrapper[] childDevices = getChildDevices()
    String[] childDeviceIds = childDevices*.getDataValue("deviceId")
    logDebug "Child device IDs: ${childDeviceIds}"

    for (DeviceWrapper childDevice in childDevices) {
        if (!kwiksetDeviceIds.contains(childDevice.getDataValue("deviceId"))) {
            logDebug "Deleting child device: ${childDevice}"
            deleteChildDevice childDevice.deviceNetworkId
        }
    }
    for (Map kwiksetDevice in kwiksetDevices) {
        if (!childDeviceIds.contains(kwiksetDevice.deviceid)) {
            createChildDevice kwiksetDevice
        }
    }
    refreshDevices()
}

void uninstalled() {
    for (DeviceWrapper childDevice in getChildDevices()) {
        logDebug "Deleting child device: ${childDevice}"
        deleteChildDevice childDevice.deviceNetworkId
    }
}

void refreshTokenAuth(boolean isInstalling = false) {
    String methodName = "refreshTokenAuth"
    Map request = [
        uri: "https://cognito-idp.us-east-1.amazonaws.com",
        headers: [
            "X-Amz-Target": "AWSCognitoIdentityProviderService.InitiateAuth",
            "Content-Type": "application/x-amz-json-1.1"
        ],
        body: [
            "AuthFlow": "REFRESH_TOKEN_AUTH",
            "AuthParameters": ["REFRESH_TOKEN": refreshToken],
            "ClientId": "5eu1cdkjp1itd1fi7b91m6g79s"
        ]
    ]
    logDebug "${methodName} request: ${request}"

    try {
        httpPostJson(request) {response ->
            logDebug "${methodName} response: ${response.data}"
            runIn(response.data.AuthenticationResult.ExpiresIn - refreshSecondsBeforeTokenExpiration, "refreshTokenAuth")
            atomicState.idToken = response.data.AuthenticationResult.IdToken
        }
    } catch (HttpResponseException e) {
        logError methodName, e
        if (isInstalling) {
            throw e
        }
        runIn(retryIntervalInSeconds, methodName)
    }
}

void refreshDevices() {
    for (Map kwiksetDevice in getKwiksetDevices()) {
        DeviceWrapper childDevice = getChildDevice(getDeviceNetworkId(kwiksetDevice))
        String deviceName = childDevice.getLabel()

        childDevice.sendEvent(name: "battery", value: kwiksetDevice.batterypercentage, unit: "%")
        logEvent "${deviceName} battery is ${kwiksetDevice.batterypercentage}%"

        String lockValue = kwiksetDevice.lockstatus.toLowerCase();
        childDevice.sendEvent(name: "lock", value: lockValue)
        logEvent "${deviceName} is ${lockValue}"
    }
    runIn(refreshIntervalInSeconds, "refreshDevices")
}

void lockOrUnlock(DeviceWrapper childDevice, String action) {
    String methodName = "lockOrUnlock"
    String deviceId = childDevice.getDataValue("deviceId")
    Map request = withAuthHeader([
        uri: "https://ynk95r1v52.execute-api.us-east-1.amazonaws.com/prod_v1/devices/${deviceId}/status",
        contentType: "text/plain", // "application/json" results in Bad Request!
        body: JsonOutput.toJson([action: action])
    ])
    logDebug "${methodName} request: ${request}"

    try {
        httpPatch(request) {response -> logDebug "${methodName} response: ${response.data}"}
    } catch (HttpResponseException e) {
        logError methodName, e
        runIn(retryIntervalInSeconds, methodName, [data: action])
    }
    runIn(refreshDelayAfterLockOrUnlock, "refreshDevices")
}

private Map[] getKwiksetDevices(boolean isInstalling = false) {
    String methodName = "getKwiksetDevices"
    Map request = withAuthHeader([
        uri: "https://ynk95r1v52.execute-api.us-east-1.amazonaws.com/prod_v1/homes/${homeId}/devices"
    ])
    logDebug "${methodName} request: ${request}"

    try {
        httpGet(request) {response ->
            logDebug "${methodName} response: ${response.data}"
            return response.data.data
        }
    } catch (HttpResponseException e) {
        logError methodName, e
        if (isInstalling) {
            throw e
        }
    }
}

private Map withAuthHeader(Map request) {
    request.headers = ["Authorization": "Bearer ${atomicState.idToken}"]
    return request
}

private void createChildDevice(Map kwiksetDevice) {
    String lockName = kwiksetDevice.devicename.toLowerCase().endsWith(" lock") ? kwiksetDevice.devicename : "${kwiksetDevice.devicename} Lock"
    String deviceNetworkId = getDeviceNetworkId(kwiksetDevice)
    logDebug "Adding child device named ${lockName} with Device Network ID: ${deviceNetworkId}"
    DeviceWrapper childDevice = addChildDevice("mingaldrichgan", "Kwikset Halo Lock", deviceNetworkId, [
        name: "Kwikset ${kwiksetDevice.modelnumber}",
        label: lockName
    ])
    childDevice.updateDataValue "deviceId", kwiksetDevice.deviceid
}

private String getDeviceNetworkId(Map kwiksetDevice) {
    return "${kwiksetDevice.modelnumber}-${kwiksetDevice.deviceid}"
}

private void logDebug(String message) {
    if (isDebugLoggingEnabled) {
        log.debug message
    }
}

private void logEvent(String message) {
    if (isEventLoggingEnabled) {
        log.info message
    }
}

private void logError(String methodName, HttpResponseException e) {
    log.error "${methodName} failed: ${e.getLocalizedMessage()}: ${e.response.data}"
}
