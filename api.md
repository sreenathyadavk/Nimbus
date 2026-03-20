# Local Photo Backup REST API Specifications

## Authentication

### `POST /api/auth/register`
Creates a new User.
- **Body**: `{ "username": "sreenath", "password": "password", "email": "email@test.com" }`
- **Response**: `200 OK` (MessageResponse)

### `POST /api/auth/login`
Authenticates and returns JWT.
- **Body**: `{ "username": "sreenath", "password": "password" }`
- **Response**: `200 OK` (JwtResponse - Contains Token)

---

## Media Operations (Requires Bearer JWT)

### `POST /api/media/upload`
Uploads a media file.
- **Content-Type**: `multipart/form-data`
- **Parts**: 
  - `file`: Multipart binary
  - `hash`: SHA-256 hash
  - `originalCreationDate`: ISO date
  - `deviceId`: String
- **Response**: `201 CREATED` (Media object)

### `GET /api/media`
Returns all active media for the user.
- **Response**: `200 OK` (Array of Media)

### `GET /api/media/{id}`
Get specific media metadata.
- **Response**: `200 OK` (Media)

### `DELETE /api/media/{id}`
Soft deletes a media file.
- **Response**: `200 OK`

### `GET /api/media/hash/{hash}`
Checks if a hash exists server-side. Used for client de-duplication.
- **Response**: `200 OK` (`{ "exists": true }`)

### `GET /api/media/sync`
Gets active media, optionally filtering by date.
- **Query Param**: `since` (ISO Date, optional)
- **Response**: `200 OK` (Array of Media)
