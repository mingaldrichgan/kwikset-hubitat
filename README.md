# Kwikset Halo integration for Hubitat

[![License](https://img.shields.io/github/license/explosivo22/rinnaicontrolr-ha?style=for-the-badge)](https://opensource.org/licenses/Apache-2.0)

## WARNING

This integration uses an [undocumented API](https://pypi.org/project/aiokwikset/) and may break at any time.

## PRE-REQUISITES

* You need a computer capable of running Python.
* You need to have the Kwikset mobile app with at least one home.
* Your Kwikset account needs to be associated with a phone number capable of receiving text messages (to get the verification code during setup).
* You need a Hubitat hub (duh).

## SETUP INSTRUCTIONS

### On your computer

* Run `pip install aiokwikset`.
* Download [`kwikset-halo-setup.py`](https://raw.githubusercontent.com/mingaldrichgan/kwikset-hubitat/main/kwikset-halo-setup.py) and `cd` to the download directory.
* Run `python kwikset-halo-setup.py` and follow the prompts.
