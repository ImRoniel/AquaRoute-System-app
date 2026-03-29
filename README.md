V# 🚢 AquaRoute Mobile: Live Tracking & Maritime Navigation

> **A high-performance Android application delivering real-time cargo visibility, dynamic weather routing, and vessel tracking.**

## 🚀 Overview
Built with a responsive MVVM architecture, the AquaRoute mobile app empowers passengers and logistics buyers to track ferries and cargo shipments in real-time. It processes live Firebase snapshots natively and is specifically engineered to handle the intermittent network connectivity common in maritime travel.

## ⚙️ Tech Stack & Architecture
* **Platform:** Android (Android Studio)
* **Architecture:** MVVM (Model-View-ViewModel)
* **Backend Integration:** Firebase SDK (Firestore, Auth, Cloud Messaging)
* **Mapping & Location:** Google Play Services, Custom `LocationHelper`

## 🔑 Key Engineering Features
* **Live Cargo Tracking:** Buyers input a unique Reference Number to view live vessel positions and ETAs on an interactive map, rendering in under 2 seconds.
* **Dynamic Proximity Filtering:** Uses device GPS to surface nearby active ports and relevant weather advisories instantly.
* **Offline Resilience:** Implements Firestore local caching (`persistenceEnabled`) to securely display last-known data when vessels hit dead zones at sea.

## 🧠 Smart Data Handling
* **Edge Calculation:** Calculates Haversine proximity distances directly within the `WeatherAdvisoriesViewModel`, saving expensive server-side queries and improving UI speed.
* **Optimized Data Fetching:** Firestore data usage is strictly minimized using `.limit(50)` on queries, efficiently handling up to 50 simultaneous vessel snapshots per map view without memory leaks.
* **Graceful Degradation:** Features an automated "Retry Location" mechanism if GPS locks fail, providing clear, non-blocking UI feedback.

---
*Developed by Roniel Cuaresma - Lead Developer (BSIT)*