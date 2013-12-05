GlassWifiConnect
================

An app to make Google Glass connect to a WPA Enterprise network.

WARNING: This method of connecting to an enterprise network does not require a valid certificate while connecting
and may make your username and password vulnerable to interception


##The short version

1. Install GlassWifiConnect.apk ([available here](https://github.com/jzplusplus/GlassWifiConnect/raw/master/bin/GlassWifiConnect.apk)) onto Glass
2. Generate a QR code using the following string, substituting the correct info for your WiFi network:  
    * WIFI:S:ssid;U:username;P:password;E:PEAP;PH:MS-CHAPv2;;
3. Say "ok, glass add wifi network" to run the app and scan the QR code. Glass should connect in a few seconds and you're done!


##The long version
This app is based off of the [ZBar example app](https://github.com/ZBar/ZBar) for QR code reading and
[this StackOverflow response](http://stackoverflow.com/a/4375874/1792555) for connecting to the WiFi network.

To use this app, you can either build from source using Android Developer Tools or just use the precompiled APK in the
bin folder. You can load it onto your Glass in ADT or using the ADB tool. You need to have one of these in order to get
the app onto Glass currently.

You can run the app by saying "ok glass, add wifi network".

Then, make your QR code format string. The details on this can be found in QRformat.txt, but it should look like this:  
* WIFI:S:ssid;U:username;P:password;E:PEAP;PH:MS-CHAPv2;;

In addition to the obvious fields, E is for the EAP method and PH is for phase 2 authentication method. If your network
doesn't require one or multiple fields, you should simply leave them out of the string. For example, without phase 2 auth:  
* WIFI:S:ssid;U:username;P:password;E:PEAP;;

NOTE: Be aware that online QR code generators may or may not send the text (containing your password) in the clear to their servers.
    
Now say "ok, glass add wifi network" to run the app on Glass and scan the generated QR code. You should see a "Connecting to..." message, followed shortly by
a "Connected!" message if everything worked properly.
