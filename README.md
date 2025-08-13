# Palm App

This is an Android application built with Kotlin and Jetpack Navigation Component. It demonstrates basic navigation between fragments, user input validation, and the use of Safe Args for passing data.

## Features

- **Home Screen**: Allows users to enter a User ID and navigate to the Register screen.
- **Register Screen**: Displays JSON data passed from the Home screen and includes a placeholder for QR code generation and a resend button.
- **Navigation**: Utilizes Android's Navigation Component for seamless transitions between fragments.
- **Safe Args**: Employs Safe Args for type-safe argument passing between navigation destinations.

## Usage of the app

1. Enter userid in the Home screen.
2. Click on "Start" button to start registration.
3. On the next screen (Registration) Scan the QR code using the Palmki scanner.
4. If needed click on "Resend" button to receive a new QR code.
5. Click "Next" to go to the confirmation screen.
6. Scan the QR code displayed on the Palmki registration device to confirm the registration.
   (Note: The app will also receive part of the Palmki-hash for later use. The Hash is shown below the camera window)
7. Click "Next" to go to the BLE advertising screen.
8. Click "Start Advertising" to start the BLE advertising.
9. If a Palmki verification device picks up the BLE package it will connect ith the app using GATT protocol. The app will send the registered part of the Palmki-hash to the device.
10. For the connection the user has to give authorisation on both the app and the device. In the app popups will appear when the GATT connection is created and stopped.
11. In the current version of the palmki scanner with GATT connection (https://github.com/BiekeSurfOrg/palm-BLE-GATT-scanner) the server will display the received data.

## Installation and Build Instructions

To set up and run this project on your local machine, follow these steps:

### Prerequisites

- Android Studio (Bumblebee 2021.1.1 or later recommended)
- Android SDK (API Level 33 or higher)
- Java Development Kit (JDK) 11 or higher

### Steps

1.  **Clone the Repository**:

    ```bash
    git clone https://github.com/your-repo/palm-app.git
    cd palm-app
    ```

    _(Note: Replace `https://github.com/your-repo/palm-app.git` with the actual repository URL if available.)_

2.  **Open in Android Studio**:
    Open the `palm-app` project in Android Studio. Android Studio should automatically detect the Gradle project.

3.  **Sync Project with Gradle Files**:
    Once the project is open, Android Studio will likely prompt you to sync the project with Gradle files. If not, you can manually sync by going to `File` > `Sync Project with Gradle Files`.

4.  **Build the Project**:
    To build the project, go to `Build` > `Make Project` or click the hammer icon in the toolbar. This will compile the application.

5.  **Run on an Emulator or Device**:

    - **Connect an Android Device**: Enable USB debugging on your Android device and connect it to your computer.
    - **Start an Android Emulator**: In Android Studio, go to `Tools` > `Device Manager` to create and start an Android Virtual Device (AVD).

    Once a device or emulator is ready, select it from the target device dropdown in the toolbar and click the green 'Run' button (triangle icon) to install and launch the app.

## Project Structure Highlights

- `app/build.gradle.kts`: Module-level Gradle build file, where dependencies and Android-specific configurations are defined, including the Safe Args plugin.
- `app/src/main/java/com/example/palm_app/`: Contains the Kotlin source code for the application's activities and fragments.
- `app/src/main/res/navigation/mobile_navigation.xml`: The navigation graph defining the app's navigation flow and arguments for Safe Args.
- `app/src/main/res/layout/`: Contains the XML layout files for the user interface.

Feel free to explore the code and modify it as needed.
