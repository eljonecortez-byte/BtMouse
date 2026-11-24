# BT Mouse (Android)

Turns your Android phone (Android 9+) into a Bluetooth mouse/touchpad.
No computer software needed.

## Build
1. Install Android Studio.
2. File > Open > select this BtMouse folder. Let Gradle sync.
3. Plug in your phone (USB debugging on) and press Run.
   Or: Build > Build APK(s), then copy app-debug.apk to the phone and install.

## Use
1. Open the app, allow the Bluetooth permission.
2. On the computer: Bluetooth settings > add device > pick your phone.
   (Do this once. Phone Bluetooth settings may also need to be visible/discoverable.)
3. In the app tap "Choose computer", pick the computer.
4. Use the pad:
   - one finger: move
   - tap: left click
   - two-finger tap: right click
   - two-finger drag: scroll
   - LEFT / MIDDLE / RIGHT buttons can be held for dragging

## Notes
- Some phones (certain Xiaomi/Huawei/Samsung builds) have broken Bluetooth HID Device support.
  If the app says "Could not register as a mouse", that's the phone, not the code.
- If paired but cursor doesn't move: unpair on both sides, restart the app, pair again.
