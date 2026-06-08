# ChalO — Architecture (v4)

## Product Philosophy

**Experience-first.** Users discover adventures through interests/tags, not categories. Every design decision flows from this.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Web layer | Spring MVC |
| Security | Spring Security |
| Persistence | Spring Data JPA (Hibernate) |
| Database | MySQL 8.x |
| Build | Maven |
| Templates | Thymeleaf |
| Markup / Style | HTML + CSS + Bootstrap 5 |
| Client scripting | Vanilla JavaScript |
| Chat | DB-driven polling — JS `fetch()` every 5 s, messages stored in MySQL |
| Location autocomplete | OpenStreetMap Nominatim API (free, no key) |
| File uploads | Local disk or Cloudinary (configurable) |

**Not used:** WebSocket, STOMP, SockJS, Node.js, React, Angular, Vue, MongoDB, Firebase, Redis, Kafka

---

## Entity Relationship Design

### Relationships at a Glance

```
users
  │
  ├──< user_interests >──────────────────── tags
  │                                           │
  ├──< adventures (host_id)                   │
  │       │                                   │
  │       ├──< adventure_tags >───────────────┘
  │       ├──< adventure_photos
  │       │
  │       ├──< join_requests
  │       │       └── on ACCEPT ──> chat_participants (add requester)
  │       │
  │       └──── chats (1 per adventure, created on first acceptance)
  │                  │
  │                  ├──< chat_participants >── users
  │                  └──< messages (polled every 5 s via JS fetch)
  │
  └──< join_requests (requester_id)
```

---

## Database Schema (MySQL)

All primary keys are `BIGINT AUTO_INCREMENT`. All foreign keys are `BIGINT`.

---

### `users`
```sql
CREATE TABLE users (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL,
    email       VARCHAR(255)  NOT NULL UNIQUE,
    password    VARCHAR(255)  NOT NULL,
    avatar_url  VARCHAR(500),
    bio         TEXT,
    phone       VARCHAR(20),
    age         INT,
    gender      ENUM('MALE','FEMALE','OTHER','PREFER_NOT_TO_SAY'),
    city        VARCHAR(100),
    is_admin    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP
);
```

---

### `tags`
```sql
CREATE TABLE tags (
    id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(50)  NOT NULL UNIQUE,
    slug  VARCHAR(50)  NOT NULL UNIQUE,
    icon  VARCHAR(100)
);
```

**Seed data:** Trekking, Camping, Photography, Beach, Road Trip, Sunrise, Waterfall, Food Exploring, Scuba, Cycling

---

### `user_interests` *(junction — drives "Recommended For You")*
```sql
CREATE TABLE user_interests (
    user_id  BIGINT NOT NULL,
    tag_id   BIGINT NOT NULL,
    PRIMARY KEY (user_id, tag_id),
    CONSTRAINT fk_ui_user FOREIGN KEY (user_id) REFERENCES users(id)  ON DELETE CASCADE,
    CONSTRAINT fk_ui_tag  FOREIGN KEY (tag_id)  REFERENCES tags(id)   ON DELETE CASCADE
);
```

---

### `adventures`
```sql
CREATE TABLE adventures (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    host_id          BIGINT        NOT NULL,
    title            VARCHAR(150)  NOT NULL,
    description      TEXT,
    adventure_date   DATE          NOT NULL,
    location_name    VARCHAR(255),
    location_lat     DECIMAL(9,6),
    location_lng     DECIMAL(9,6),
    max_participants INT           NOT NULL,
    cover_image_url  VARCHAR(500),
    status           ENUM('DRAFT','PUBLISHED','COMPLETED','CANCELLED')
                                   NOT NULL DEFAULT 'DRAFT',
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_adventure_host FOREIGN KEY (host_id) REFERENCES users(id)
);
```

**No `difficulty_level` — removed from MVP.**

---

### `adventure_tags` *(junction)*
```sql
CREATE TABLE adventure_tags (
    adventure_id  BIGINT NOT NULL,
    tag_id        BIGINT NOT NULL,
    PRIMARY KEY (adventure_id, tag_id),
    CONSTRAINT fk_at_adventure FOREIGN KEY (adventure_id) REFERENCES adventures(id) ON DELETE CASCADE,
    CONSTRAINT fk_at_tag       FOREIGN KEY (tag_id)       REFERENCES tags(id)       ON DELETE CASCADE
);
```

---

### `adventure_photos`
```sql
CREATE TABLE adventure_photos (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    adventure_id   BIGINT       NOT NULL,
    url            VARCHAR(500) NOT NULL,
    caption        VARCHAR(255),
    display_order  INT          NOT NULL DEFAULT 0,
    uploaded_by    BIGINT       NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_photo_adventure FOREIGN KEY (adventure_id) REFERENCES adventures(id) ON DELETE CASCADE,
    CONSTRAINT fk_photo_uploader  FOREIGN KEY (uploaded_by)  REFERENCES users(id)
);
```

---

### `join_requests`
```sql
CREATE TABLE join_requests (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    adventure_id  BIGINT NOT NULL,
    requester_id  BIGINT NOT NULL,
    message       TEXT,
    status        ENUM('PENDING','ACCEPTED','REJECTED') NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_adventure_requester UNIQUE (adventure_id, requester_id),
    CONSTRAINT fk_jr_adventure FOREIGN KEY (adventure_id) REFERENCES adventures(id) ON DELETE CASCADE,
    CONSTRAINT fk_jr_requester FOREIGN KEY (requester_id) REFERENCES users(id)
);
```

---

### `chats`
```sql
CREATE TABLE chats (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    adventure_id  BIGINT    NOT NULL UNIQUE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_adventure FOREIGN KEY (adventure_id) REFERENCES adventures(id) ON DELETE CASCADE
);
```

Chat is created only when the host accepts the first join request. Subsequent acceptances add users to the same chat.

---

### `chat_participants`
```sql
CREATE TABLE chat_participants (
    chat_id    BIGINT    NOT NULL,
    user_id    BIGINT    NOT NULL,
    joined_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (chat_id, user_id),
    CONSTRAINT fk_cp_chat FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE,
    CONSTRAINT fk_cp_user FOREIGN KEY (user_id) REFERENCES users(id)
);
```

---

### `messages`
```sql
CREATE TABLE messages (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    chat_id    BIGINT    NOT NULL,
    sender_id  BIGINT    NOT NULL,
    content    TEXT      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_msg_chat   FOREIGN KEY (chat_id)   REFERENCES chats(id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_sender FOREIGN KEY (sender_id) REFERENCES users(id)
);
```

---

## JPA Entity Summary

| Entity | Table | Key Relationships |
|---|---|---|
| `User` | users | `@ManyToMany` interests (tags), `@OneToMany` adventures (host), `@OneToMany` joinRequests |
| `Tag` | tags | — (no back-references needed) |
| `Adventure` | adventures | `@ManyToOne` host, `@ManyToMany` tags, `@OneToMany` photos, `@OneToMany` joinRequests, `@OneToOne` chat |
| `AdventurePhoto` | adventure_photos | `@ManyToOne` adventure, `@ManyToOne` uploadedBy |
| `JoinRequest` | join_requests | `@ManyToOne` adventure, `@ManyToOne` requester |
| `Chat` | chats | `@OneToOne` adventure, `@OneToMany` participants, `@OneToMany` messages |
| `ChatParticipant` | chat_participants | `@EmbeddedId` composite PK, `@ManyToOne` chat, `@ManyToOne` user |
| `Message` | messages | `@ManyToOne` chat, `@ManyToOne` sender |

---

## Maven Project Structure

```
chalo/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/chalo/
    │   │   ├── ChalOApplication.java
    │   │   │
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java       -- BCrypt, form login, role guards
    │   │   │   └── WebMvcConfig.java         -- resource handlers
    │   │   │
    │   │   ├── controller/
    │   │   │   ├── AuthController.java       -- /register, /login
    │   │   │   ├── AdventureController.java  -- /explore, /adventures/**
    │   │   │   ├── JoinRequestController.java
    │   │   │   ├── ChatController.java       -- /chats/**
    │   │   │   ├── UserController.java       -- /users/:id, /dashboard
    │   │   │   ├── AdminController.java      -- /admin
    │   │   │   └── ApiController.java        -- /api/** JSON endpoints
    │   │   │
    │   │   ├── model/
    │   │   │   ├── User.java
    │   │   │   ├── Tag.java
    │   │   │   ├── Adventure.java
    │   │   │   ├── AdventurePhoto.java
    │   │   │   ├── JoinRequest.java
    │   │   │   ├── Chat.java
    │   │   │   ├── ChatParticipant.java
    │   │   │   ├── ChatParticipantId.java
    │   │   │   ├── Message.java
    │   │   │   ├── Gender.java               -- enum
    │   │   │   ├── AdventureStatus.java      -- enum
    │   │   │   └── JoinRequestStatus.java    -- enum
    │   │   │
    │   │   ├── repository/
    │   │   │   ├── UserRepository.java
    │   │   │   ├── TagRepository.java
    │   │   │   ├── AdventureRepository.java
    │   │   │   ├── AdventurePhotoRepository.java
    │   │   │   ├── JoinRequestRepository.java
    │   │   │   ├── ChatRepository.java
    │   │   │   ├── ChatParticipantRepository.java
    │   │   │   └── MessageRepository.java
    │   │   │
    │   │   ├── service/
    │   │   │   ├── UserService.java
    │   │   │   ├── AdventureService.java
    │   │   │   ├── JoinRequestService.java
    │   │   │   ├── ChatService.java
    │   │   │   ├── LocationService.java
    │   │   │   └── AdminService.java
    │   │   │
    │   │   ├── dto/
    │   │   │   ├── AdventureSearchForm.java
    │   │   │   ├── AdventureForm.java
    │   │   │   ├── JoinRequestForm.java
    │   │   │   ├── HostStatsDto.java
    │   │   │   └── LocationSuggestionDto.java
    │   │   │
    │   │   └── security/
    │   │       ├── CustomUserDetails.java
    │   │       └── CustomUserDetailsService.java
    │   │
    │   └── resources/
    │       ├── application.properties
    │       ├── schema.sql
    │       ├── data.sql                      -- tag seed data
    │       ├── static/
    │       │   ├── css/chalo.css
    │       │   └── js/
    │       │       ├── location-autocomplete.js
    │       │       ├── tag-filter.js
    │       │       └── chat-poll.js          -- fetch() every 5 s
    │       └── templates/
    │           ├── layout/base.html
    │           ├── auth/{login,register}.html
    │           ├── adventure/{explore,detail,form,requests}.html
    │           ├── chat/{list,room}.html
    │           ├── user/{dashboard,profile}.html
    │           └── admin/panel.html
    └── test/java/com/chalo/...
```

---

## Key `pom.xml` Dependencies

```xml
<dependency>spring-boot-starter-web</dependency>
<dependency>spring-boot-starter-thymeleaf</dependency>
<dependency>spring-boot-starter-security</dependency>
<dependency>spring-boot-starter-data-jpa</dependency>
<dependency>spring-boot-starter-validation</dependency>
<dependency>thymeleaf-extras-springsecurity6</dependency>
<dependency>mysql-connector-j</dependency>
<dependency>lombok</dependency>
```

No WebSocket dependency.

---

## URL Routes (Spring MVC)

### Public
| Method | URL | Description |
|---|---|---|
| GET | `/` | Landing page |
| GET | `/explore` | Search by tags + optional location |
| GET | `/adventures/{id}` | Adventure detail |
| GET | `/host/{userId}` | Host public profile |
| GET | `/login` | Login form |
| POST | `/login` | Spring Security processes login |
| GET | `/register` | Register form |
| POST | `/register` | Process registration |

### Authenticated
| Method | URL | Description |
|---|---|---|
| GET | `/dashboard` | My adventures + Recommended For You |
| GET | `/adventures/new` | Create form |
| POST | `/adventures` | Save new adventure |
| GET | `/adventures/{id}/edit` | Edit form |
| POST | `/adventures/{id}` | Update adventure |
| POST | `/adventures/{id}/join` | Submit join request |
| GET | `/adventures/{id}/requests` | Manage requests (host only) |
| POST | `/adventures/{id}/requests/{reqId}/accept` | Accept |
| POST | `/adventures/{id}/requests/{reqId}/reject` | Reject |
| GET | `/chats` | Chat list |
| GET | `/chats/{id}` | Chat room |
| GET | `/profile` | My profile |

### JSON API (called by JavaScript)
| Method | URL | Description |
|---|---|---|
| GET | `/api/locations/suggest?q=` | Nominatim autocomplete |
| GET | `/api/tags` | All tags |
| GET | `/api/chats/{id}/messages?after={lastId}` | Poll new messages (chat-poll.js) |

### Admin
| Method | URL | Description |
|---|---|---|
| GET | `/admin` | Single-page admin panel |
| POST | `/admin/users/{id}/ban` | Ban user |
| POST | `/admin/adventures/{id}/cancel` | Cancel adventure |
| POST | `/admin/tags` | Add tag |
| POST | `/admin/tags/{id}/delete` | Delete tag |

---

## Key Business Logic

### Join Request → Chat Flow
```
acceptRequest(requestId):
  1. Load JoinRequest, verify caller is adventure host
  2. Set status = ACCEPTED, save
  3. chatRepository.findByAdventureId(adventureId)
     IF not found:
       create Chat(adventure), save
       add host as ChatParticipant
  4. Add requester as ChatParticipant (if not already present)
  5. Redirect to /chats/{chatId}
```

### "Recommended For You" Query
```java
// Dashboard: adventures whose tags overlap with user's interests
// Ordered by number of matching tags descending
@Query("""
    SELECT a, COUNT(t) AS matchCount
    FROM Adventure a
    JOIN a.tags t
    WHERE t IN :userInterests
      AND a.status = 'PUBLISHED'
      AND a.adventureDate >= CURRENT_DATE
      AND a.host.id <> :userId
    GROUP BY a
    ORDER BY matchCount DESC
""")
List<Adventure> findRecommended(@Param("userInterests") Set<Tag> userInterests,
                                @Param("userId") Long userId);
```
No AI required — pure SQL `ORDER BY COUNT(matching tags) DESC`.

### Chat Polling (chat-poll.js)
```javascript
// Runs every 5 seconds
setInterval(() => {
  fetch(`/api/chats/${chatId}/messages?after=${lastMessageId}`)
    .then(r => r.json())
    .then(messages => {
      messages.forEach(appendMessage);
      if (messages.length > 0) lastMessageId = messages.at(-1).id;
    });
}, 5000);
```

### Adventure Search Query
```java
@Query("""
    SELECT DISTINCT a FROM Adventure a
    JOIN a.tags t
    WHERE t.id IN :tagIds
      AND (:location IS NULL OR a.locationName LIKE %:location%)
      AND a.status = 'PUBLISHED'
      AND a.adventureDate >= CURRENT_DATE
    ORDER BY a.adventureDate ASC
""")
List<Adventure> search(@Param("tagIds") List<Long> tagIds,
                       @Param("location") String location);
```

### Host Statistics (derived, no stored counters)
```java
long totalAdventures  = adventureRepo.countByHostId(hostId);
long totalParticipants = joinRequestRepo.countAcceptedByHostId(hostId);
LocalDate memberSince = user.getCreatedAt().toLocalDate();
```

---

## Thymeleaf Page Notes

### Dashboard (`dashboard.html`)
```
My Upcoming Adventures  (joined + hosting)
─────────────────────────────────────────
Recommended For You  ← driven by user_interests + ORDER BY match count
  🏔 Based on: Trekking · Photography
  [adventure cards...]
```

### Adventure Detail (`detail.html`)
```
Cover image + title + tag badges + adventure_date + location_name
Photo gallery  (Bootstrap carousel, th:each photo)
─────────────────────────────────────
Description  │  Host card
             │    avatar · name · bio
             │    Stats: X adventures · Y participants · member since
             │    Previous hosted (mini cards)
             │    [Request To Join] → Bootstrap modal
─────────────────────────────────────
Request To Join modal:
  <form th:action="@{/adventures/{id}/join(id=${adventure.id})}" method="post">
    <textarea name="message" placeholder="Tell the host about yourself..."/>
    <input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>
    <button>Send Request</button>
  </form>
```

### Chat Room (`room.html`)
```
Message history (Thymeleaf initial render)
JS chat-poll.js fetches /api/chats/{id}/messages?after={lastId} every 5 s
New messages appended to DOM — no page reload
Send form POSTs to /chats/{id}/messages (standard form submit or fetch POST)
```

### Admin Panel (`panel.html`) — Single Page, Bootstrap Tabs
```
Overview | Users | Adventures | Requests | Tags
```

---

## application.properties (skeleton)

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/chalo
spring.datasource.username=root
spring.datasource.password=
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=none
spring.sql.init.mode=always
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

spring.thymeleaf.cache=false

spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

chalo.upload.dir=uploads/
```

---

## Summary of All Design Decisions

| Decision | Implementation |
|---|---|
| BIGINT AUTO_INCREMENT PKs | `@GeneratedValue(strategy = IDENTITY)` on all entities |
| Experience-first discovery | Multi-tag `@ManyToMany` via `adventure_tags`; search by tag IDs |
| User interests for recommendations | `user_interests` junction → `ORDER BY COUNT(matching tags) DESC` |
| User profile fields | `age INT`, `gender ENUM`, `city VARCHAR` added to `users` |
| No difficulty_level | Not in schema or entity |
| Single adventure_date | `LocalDate` mapped to MySQL `DATE` |
| DB-driven chat (no WebSocket) | Messages in MySQL; JS polls `/api/chats/{id}/messages` every 5 s |
| Chat gated by acceptance | `JoinRequestService` creates chat lazily on first accept |
| Location autocomplete | JS → `/api/locations/suggest` → Spring proxies Nominatim |
| Admin = single page | `/admin` with Bootstrap tabs inline |
| CSRF | Spring Security default; `${_csrf.token}` in every POST form |
