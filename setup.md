# Setup Instructions

## Backend Setup (Docker)
The backend is completely containerized. Simply run:
```bash
docker compose up -d --build
```
This will start:
1. **MongoDB** on port `27017`
2. **Spring Boot Backend** on port `8080`

The file storage will be mounted natively on `./backend/storage`.

*Configurations:*
To configure the settings such as storage paths, file size limits, or JWT lifespan, edit `application.yml` located in `backend/src/main/resources/`.

## Client Setup (Android)
1. Ensure your Android device/emulator is connected to the same network.
2. Update the `ApiClient.BASE_URL` in `client/app/src/main/java/com/localcloud/photosclient/network/ApiClient.kt` to match your server's IP address if using a physical device (e.g. `http://192.168.1.100:8080/`). For emulator, `http://10.0.2.2:8080/` works out of the box.
3. Open the `client` folder in Android Studio.
4. Sync Gradle to pull dependencies.
5. Hit Run.
