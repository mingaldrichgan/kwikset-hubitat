/*
    Copyright 2022 Ming Aldrich-Gan.
    Thanks to https://pypi.org/project/aiokwikset/ for reverse-engineering the Kwikset API.
*/
import com.hubitat.app.ChildDeviceWrapper
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
    page(name: "mainPage", title: "Setup", install: true, uninstall: true) {
        section {
            input "homeId", "text", title: "Home ID", required: true
            input "refreshToken", "textarea", title: "Refresh Token", required: true, rows: 20
            input "refreshIntervalInSeconds", "number", title: "Refresh Interval (seconds)", defaultValue: 60
            input "retryIntervalInSeconds", "number", title: "Retry Interval (seconds)", defaultValue: 30
            input "isDebugEnabled", "bool", title: "Enable Debug Logging"
        }
    }
}

void installed() {
    refreshTokenAuth(true)
    for (Map kwiksetDevice in getKwiksetDevices(true)) {
        addChildDevice kwiksetDevice
    }
    refreshDevices()
}

void updated() {
    refreshTokenAuth(true)

    Map[] kwiksetDevices = getKwiksetDevices(true)
    String[] kwiksetDeviceIds = kwiksetDevices.deviceid
    logDebug "Kwikset device IDs present: ${kwiksetDeviceIds}"

    ChildDeviceWrapper[] childDevices = getChildDevices()
    String[] childDeviceIds = childDevices.deviceNetworkId
    logDebug "Child device IDs present: ${childDeviceIds}"

    for (ChildDeviceWrapper childDevice in childDevices) {
        if (!kwiksetDeviceIds.contains(childDevice.deviceNetworkId)) {
            logDebug "Deleting child device: ${childDevice}"
            deleteChildDevice childDevice.deviceNetworkId
        }
    }
    for (Map kwiksetDevice in kwiksetDevices) {
        if (!childDeviceIds.contains(kwiksetDevice.deviceid)) {
            addChildDevice kwiksetDevice
        }
    }
    refreshDevices()
}

void uninstalled() {
    for (ChildDeviceWrapper childDevice in getChildDevices()) {
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
            runIn(response.data.AuthenticationResult.ExpiresIn, "refreshTokenAuth")
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
        ChildDeviceWrapper childDevice = getChildDevice(kwiksetDevice.deviceid)
        childDevice.sendEvent(name: "battery", value: kwiksetDevice.batterypercentage)
        childDevice.sendEvent(name: "lock", value: kwiksetDevice.lockstatus.toLowerCase())
    }
    runIn(refreshIntervalInSeconds, "refreshDevices")
}

void lockOrUnlock(String deviceId, String action) {
    String methodName = "lockOrUnlock"
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
    refreshDevices()
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

private void addChildDevice(Map kwiksetDevice) {
    String lockName = kwiksetDevice.devicename + " Lock"
    logDebug "Adding child device named ${lockName} with ID: ${kwiksetDevice.deviceid}"
    addChildDevice "mingaldrichgan", "Kwikset Halo Lock", kwiksetDevice.deviceid, [name: lockName]
}

private void logDebug(String message) {
    if (isDebugEnabled) {
        log.debug message
    }
}

private void logError(String methodName, HttpResponseException e) {
    log.error "${methodName} failed: ${e.getLocalizedMessage()}: ${e.response.data}"
}