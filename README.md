# LocationReminders
A TODO list app with location reminders that remind the user to do something when the user is at a specific location. The app will require the user to create an account and login to set and access reminders. Part of the Advanced Android Kotlin Development nanodegree from Udacity.

|Welcome|Login|Home|Create Reminder|
|:---:|:---:|:---:|:---:|
|<img src="screenshot/1_welcome.jpg" alt="home screenshot" width=150/>|<img src="screenshot/2_login.jpg" alt="detail screenshot" width=150/>|<img src="screenshot/3_home.jpg" alt="transfer screenshot" width=150/>|<img src="screenshot/4_create_reminder.jpg" alt="complete screenshot" width=150/>|

|Select Location|Map View|Save Reminder|Reminder Saved|
|:---:|:---:|:---:|:---:|
|<img src="screenshot/5_select_location.jpg" alt="home screenshot" width=150/>|<img src="screenshot/6_map_view.jpg" alt="detail screenshot" width=150/>|<img src="screenshot/7_save_reminder.jpg" alt="transfer screenshot" width=150/>|<img src="screenshot/8_reminder_saved.jpg" alt="complete screenshot" width=150/>|

_[Directions to get a Google Maps API key](https://developers.google.com/maps/documentation/android-sdk/get-api-key)_
<br>
_You can add your API key in [google_maps_api.xml string resource](app/src/debug/res/values/google_maps_api.xml)_

The app lets the user save a reminder by entering a title, optional description, and a location via Google map.
Permission is asked initially for the current location and the when current location icon is tapped, but not mandatory.
Constant location access is then asked for geofencing by opening device settings and notifying the user when proceeding without the permission.

Topics:
- Google Maps API
- FirebaseUI
- Permissions
- Repository Pattern
- Notifications
- Testing
- UI Testing
- Geofence
