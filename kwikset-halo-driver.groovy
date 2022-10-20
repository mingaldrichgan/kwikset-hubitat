/*
    Copyright 2022 Ming Aldrich-Gan.
    Thanks to https://pypi.org/project/aiokwikset/ for reverse-engineering the Kwikset API.
*/
metadata {
    definition(
        name: "Kwikset Halo Lock",
        namespace: "mingaldrichgan",
        author: "Ming Aldrich-Gan",
        importUrl: "https://raw.githubusercontent.com/mingaldrichgan/kwikset-hubitat/main/kwikset-halo-driver.groovy"
    ) {
        capability "Battery"
        capability "Initialize"
        capability "Lock"
        capability "Refresh"
    }
}

void initialize() {
    parent?.refreshAuthToken()
    refresh()
}

void lock() {
    parent?.lockOrUnlock device.deviceNetworkId, "lock"
    refresh()
}

void unlock() {
    parent?.lockOrUnlock device.deviceNetworkId, "unlock"
    refresh()
}

void refresh() {
    parent?.refreshDevices()
}
