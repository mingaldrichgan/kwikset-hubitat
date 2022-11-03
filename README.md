# Kwikset Halo integration for Hubitat

[![License](https://img.shields.io/github/license/explosivo22/rinnaicontrolr-ha?style=for-the-badge)](https://opensource.org/licenses/Apache-2.0)

## HELP NEEDED

This integration seems to work for a few days before the Kwikset API requests start failing with `status code: 403, reason phrase: Forbidden: {"Message":"User is not authorized to access this resource with an explicit deny"}`. If anybody has time and/or expertise to troubleshoot this, your help would be greatly appreciated!

## WARNING

This integration uses an [undocumented API](https://pypi.org/project/aiokwikset/) and may break at any time.

## PRE-REQUISITES

* You need a computer with Python installed, and know how to run terminal commands.
* You need to have the [Kwikset mobile app](https://www.kwikset.com/smart-locks/app) with at least one home added to it.
* Your Kwikset account needs to be associated with a phone number capable of receiving text messages (to get the verification code during setup).
* You need a Hubitat hub (duh).

## IN YOUR TERMINAL

* Run `pip install aiokwikset`.
* Download [`kwikset-halo-setup.py`](https://raw.githubusercontent.com/mingaldrichgan/kwikset-hubitat/main/kwikset-halo-setup.py) and `cd` to the download directory.
* Run `python kwikset-halo-setup.py` and follow the prompts to get your Home ID and Refresh Token.

## IN HUBITAT

`TODO`

![Screenshot](https://github.com/mingaldrichgan/kwikset-hubitat/raw/main/kwikset-halo-app.png)
