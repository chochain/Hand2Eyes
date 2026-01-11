# Android - Robot driven by Hand Landmarks

## Overview

An example of how to control the robot movements by Android camera app utilizing MediaPipe hand landmarks from continuous camera frames.

[![Your Video Title](https://img.youtube.com/vi/dxwCotsNItU/0.jpg)](https://www.youtube.com/watch?v=dxwCotsNItU)

## Design
1. Google MediaPipe use AI to analyze hand guestures into 21 landmarks per hand.
2. Hand2Eyes take the landmark into high-low, left-rigth by comparing index-finger and palm position.
3. The brightness of two view areas are adjusted by H/L, and L/R inputs.
4. An Arduino Nano wired with two photoresisters which coupled with the two areas.
5. The photoresisters values are read and converted into position of servos.

## Build using Android Studio

### Prerequisites

*   The **[Android Studio](https://developer.android.com/studio/index.html)** IDE. This sample has been tested on Android Studio Dolphin.

*   A physical Android device with a minimum OS version of SDK 24 (Android 7.0 -
    Nougat) with developer mode enabled. The process of enabling developer mode
may vary by device.

### Testing

Hand2Eyes has been successfully built and tested on these old Android phones.

> Phone|Android|Spec|FPS
> -----|-------|----|---
> ASUS ZenFone 3|Android 8|32-bit 2.0GHz octa-A53+Adreno|~75ms/frame on GPU
> SOYES X11 mini-phone|Android 9|64-bit, 1.3GHz quad-A7|~800ms/frame on CPU

