# myPhone 📱

A modern, minimalist, and feature-rich Android Phone application built with Jetpack Compose. Designed for efficiency, one-handed usability, and a premium user experience.

## ✨ Features

- **🚀 Smart Dialer**: Persistent dial pad state with quick access to common actions.
- **📞 Premium Call History**:
    - **Smart Grouping**: Consecutive calls from the same contact are merged to reduce clutter.
    - **One-Tap Actions**: Dedicated call and message buttons for every entry.
    - **Detailed Logs**: View call duration, time, and type (Incoming/Outgoing/Missed) at a glance.
- **💬 Categorized Messaging**:
    - **Auto-Sorting**: Messages are automatically categorized into Personal, Transactions, OTPs, and Offers.
    - **Smart Cleanup**: Built-in logic to delete old transactional and promotional messages automatically.
    - **Unread Indicators**: Clear visual cues for new messages.
- **👤 Contact Management**:
    - Seamless integration with system contacts.
    - Quick search and easy contact editing/creation.
- **🛠️ System Integration**:
    - Full **InCallService** implementation for custom call handling.
    - Handles incoming and outgoing calls with a smooth, interactive UI.
    - Proximity sensor support to prevent accidental touches during calls.
    - Background call timer and persistent notification.

## 🎨 Design Philosophy

- **Material 3**: Fully embraces the latest Android design system.
- **Glassmorphism**: Subtle translucency and modern card layouts.
- **Accessibility**: Optimized for one-handed use with key interactions positioned at the bottom of the screen.
- **Dark Mode Ready**: Beautifully adapts to system theme settings.

## 🛠️ Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (100%)
- **Architecture**: Clean Architecture with Repository pattern.
- **Networking/Data**: Android Telecom Framework, Content Resolvers for Contacts and SMS.
- **Navigation**: Compose Navigation with hoisted state management.

## 🚀 Getting Started

1. **Clone the repository**:
   ```bash
   git clone https://github.com/yourusername/myPhone.git
   ```
2. **Open in Android Studio**: Use the latest Hedgehog or Iguana version.
3. **Permissions**: The app requires typical phone permissions (Contacts, Call Logs, SMS, Phone).
4. **Set as Default**: For full functionality, set **myPhone** as your default Phone and SMS app when prompted.

## 📸 Screenshots

*(Add your screenshots here after pushing to GitHub)*

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---
Built with ❤️ by [Drosocode]
