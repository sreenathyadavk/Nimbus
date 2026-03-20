# Nimbus

Nimbus is a production-grade, self-hosted, local photo backup and gallery system.

## Tech Stack
- **Frontend (Android):** Kotlin + Jetpack Compose
- **Backend:** Spring Boot 3 + Java 17
- **Database:** MongoDB
- **Infrastructure:** Docker

## How to run backend
Ensure you have Docker and Docker Compose installed.
```bash
docker-compose up --build
```
This will start:
- **MongoDB**: For metadata storage.
- **KPhotos Backend**: The Spring Boot application.

## How to run Android
1. Open the `/client` directory in Android Studio.
2. Ensure you have the necessary permissions enabled on your device/emulator.
3. Build and run the `app` module.

## Environment variables needed
Create a `.env` file in the root directory for backend configuration:
```bash
# MongoDB Configuration
SPRING_DATA_MONGODB_URI=mongodb://mongodb:27017/photos

# Storage Configuration
STORAGE_PATH=/path/to/your/photos/storage
```
> [!NOTE]
> Make sure the `STORAGE_PATH` in `.env` matches the volume mapping in `docker-compose.yml`.
