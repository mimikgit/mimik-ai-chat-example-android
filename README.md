# Objective

The goal of this example is to demonstrate how mimik AI technology integrates into an Android application. It allows you to download an AI language model to your device, interact with it, and even use it offline.

# Prerequisites

You will need a 64-bit Android device. This sample will not work with an emulated device.

# Getting the Source Code

To begin, clone the code from GitHub and open it in Android Studio.

Execute the following command to clone the example code:

```
git clone https://github.com/mimikgit/mimik-ai-chat-example-android.git
```

# Configuring the mimik Client Library

You will need some configuration values from the [mimik Developer Console](https://console.mimik.com). Create an edge project and take note of the following 3 values:

- Edge License, found at the bottom of the [Edge Projects page](https://console.mimik.com/projects)
- Client ID, found in your project page
- Developer ID Token, found by clicking the **Get ID Token** button in your project page

Once you have these values, replace the placeholders in `app/src/main/com/mimik/aichat/Constants.kt`:

```kotlin
const val CLIENT_ID = "your-client-id"
const val DEVELOPER_ID_TOKEN = "your-developer-id-token"
const val MIM_OE_LICENSE = "your-mim-oe-license"
```

Once these values are configured, you can run the application, which will automatically begin downloading an AI language model to start chatting with.

# Additional reading

In order to get more out of this article, the reader could further familiarize themselves with the following concepts and techniques:

- [Understanding the mimik Client Library for Android](https://devdocs.mimik.com/key-concepts/11-index).
- [Creating a Simple Android Application that Uses an edge microservice](https://devdocs.mimik.com/tutorials/01-submenu/03-submenu/01-index).
- [Integrating the mimik Client Library into an Android project](https://devdocs.mimik.com/tutorials/01-submenu/03-submenu/02-index).
- [Working with edge microservices in an Android project](https://devdocs.mimik.com/tutorials/01-submenu/03-submenu/04-index).
- [Working with AI language models in an Android project](https://devdocs.mimik.com/tutorials/02-submenu/03-submenu/01-index).
