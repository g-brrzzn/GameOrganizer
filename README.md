# 🎮 GameOrganizer

GameOrganizer is a web service that searches and aggregates video game data from multiple sources, such as RAWG and Steam. It provides a simple API and a web interface to find games and view their combined details in one place.

## 📸 Screenshots

<img width="2528" height="1197" alt="GameOrganizer screenshot 1" src="https://github.com/user-attachments/assets/408efdcc-9b32-40f9-88c6-5e23351fcb34" />
<img width="2506" height="1207" alt="GameOrganizer screenshot 2" src="https://github.com/user-attachments/assets/acdf98ec-6dcb-48dd-a3a1-a9816521c7d2" />
<img width="2532" height="1223" alt="GameOrganizer screenshot 3" src="https://github.com/user-attachments/assets/7c4435bc-caa9-4206-bf7b-8f7764ec3909" />

---

## ✨ Features

* **Simple Search:** A clean web interface to search for any game by its name.
* **Data Aggregation:** Fetches primary game data (like name, genres, and background images) from the RAWG API.
* **Steam Enrichment:** Automatically finds the corresponding Steam store page and enriches the data.
* **Sorted Results:** Returns results sorted by playtime for relevance.
* **Simple API:** Provides a single, easy-to-use API endpoint for integration.

---

## 🚀 Getting Started

### Prerequisites

* Java 21
* Apache Maven

### Running the Application

1.  **Clone the repository:**
    ```sh
    git clone [https://github.com/YOUR_USERNAME/GameOrganizer.git](https://github.com/YOUR_USERNAME/GameOrganizer.git)
    cd GameOrganizer
    ```

2.  **Configure API Key (Recommended):**
    To avoid API rate limits, it is highly recommended to add your own RAWG API key. Create a file at `src/main/resources/application.properties` and add the following line:
    ```properties
    rawg.api.key=YOUR_RAWG_API_KEY_HERE
    ```

3.  **Run the application:**
    Use the Maven wrapper to build and run the project:
    ```sh
    ./mvnw spring-boot:run
    ```

4.  **Access the web interface:**
    Open `http://localhost:8080` in your browser.

---

## 📡 API Usage

The project exposes a single endpoint for fetching game data.

* **Endpoint:** `GET /api/games/organize`
* **Query Parameter:**
    * `name` (string, required): The name of the game you want to search for.

* **Example Request:**
    ```
    http://localhost:8080/api/games/organize?name=The%20Witcher%203
    ```
