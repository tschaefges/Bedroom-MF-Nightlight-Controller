This app will control a **Thirdreality Multi-function Nightlight** device on a Hubitat C7 hub. The app will be written in the Groovy language.

This device has a *local routine* mode, which is automatically activated by the firmware. We do not want *local routine* mode to be used at all. Any command from this app to the device will disable *local routine* mode for 12 hours. Therefore we need to remember when the last command was sent and issue a command to the device to prevent the reenabling of *local routine* mode before the 12-hour time limit runs out. I do not know whether sending just a simple command, like changing the color, will work or whether the light needs to be toggled on and off. The logic to do this should be isolated so we can make changes as experimentation proves the concept. 

The app will have a settings page which will allow the user to select the following devices and attributes:

|  Device   | Function    |
| --- | --- |
| bnlDevice      | Points to the Thirdreality Multi-function Nightlight device that we are going to control |
| bnlOnOffSwitch | Used to turn the function of the device on and off |
| bnlAutoSwitch  | Used to turn automatic control of the light on and off |
| luxSensor | Device to sense the illumination in the room | 


|  Attribute   | Usage    |
| --- | --- |
|  normalColor   | Color of the light when the system **mode** is anything other than *Night*   |
|  nightColor    | Color of the light when the system **mode** is *Night* |
|  luxLimit      | The threshold for turning the light on and off.|

The app should subscribe to changes in the device states and system mode and dynamically adjust its operation accordingly 

The operation of the light should be as follows, depending on the state of the switches: 

| btnOnOffSwitch | btnAutoSwitch | Operation |
| --- | --- | --- |
| Off | On or Off  |  **bnlDevice** turns off and remains off except, for every 12 hours commands needed to prevent the *local routine* from being enabled |
| On | Off | **bnlDevice** Turns on and stays on. Color to be determined by system **mode** as described in the attribute section above. |
| On | On | **bnlDevice** Turns on when **luxSensor** value is less than or equal to **luxLimit** value. Turns off when **luxSensor** value is greater than **luxLimit** value. Color to be determined by the system **mode** as described in the attributes section above. |

A future enhancement may be to turn the light on or off depending on the system mode. The logic for turning the lights on and off depending on switches and modes should be consolidated into one routine for easy modification later. 