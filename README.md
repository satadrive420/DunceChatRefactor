# DunceChat

**Version 2.1** | **Minecraft 1.21+** | **Paper/Spigot**

A comprehensive chat moderation plugin that allows server administrators to manage problematic players through a "dunce" system, complete with IP-based alt detection, automatic word filtering, and a full-featured admin interface.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Installation](#installation)
- [Commands](#commands)
- [Permissions](#permissions)
- [Configuration](#configuration)
- [Database Schema](#database-schema)
- [Architecture](#architecture)
- [Data Flow](#data-flow)
- [PlaceholderAPI Integration](#placeholderapi-integration)
- [Troubleshooting](#troubleshooting)
- [For Developers](#for-developers)

---

## Overview

DunceChat provides a non-destructive way to manage rule-breaking players. Instead of banning players outright, you can "dunce" them - placing them in a separate chat channel visible only to staff and other dunced players. This allows for:

- **Soft Moderation**: Players can still play, but their chat is isolated
- **Transparency**: Staff can monitor what dunced players are saying
- **Alt Detection**: Automatically detect and manage alt accounts via IP tracking
- **Time-Based**: Set expiration times for automatic unduncing
- **Word Filtering**: Automatically dunce players who use prohibited words

---

## Features

### Core Features

| Feature | Description |
|---------|-------------|
| **Dunce System** | Isolate players to a separate "dunce chat" visible only to staff and other dunced players |
| **Timed Dunces** | Set expiration times (e.g., `1h`, `7d`) for automatic unduncing |
| **Auto-Dunce** | Automatically dunce players who use prohibited words |
| **GUI Menu** | Visual interface for managing dunce settings |
| **Dunce Lookup** | View detailed information about dunced players |

### IP & Alt Detection

| Feature | Description |
|---------|-------------|
| **Silent IP Tracking** | Logs every IP address a player uses without notification |
| **Alt Detection** | Cross-references IPs to find linked accounts |
| **IP Duncing** | Dunce all accounts sharing an IP address |
| **Auto-Dunce on IP Match** | Optionally auto-dunce players sharing IPs with dunced players |
| **IP Whitelist** | Ignore specific IPs (schools, cafes) with CIDR support |
| **IP Watchlist** | Get alerts when players join from specific IPs |
| **Admin Notifications** | Real-time alerts when potential alts are detected |

### Chat Features

| Feature | Description |
|---------|-------------|
| **Dunce Chat** | Separate chat channel for dunced players |
| **Observer Mode** | Staff can view and participate in dunce chat |
| **Green Text** | Automatic `>greentext` formatting |
| **PlaceholderAPI** | Full support for prefixes and display names |

---

## Installation

1. **Download** the latest `DunceChat-X.X.jar` from releases
2. **Place** the JAR in your server's `plugins/` folder
3. **Restart** your server
4. **Configure** the plugin in `plugins/DunceChat/config.yml`

### Requirements

- **Minecraft**: 1.21+
- **Server**: Paper (recommended) or Spigot
- **Java**: 21+
- **Optional**: PlaceholderAPI for prefix/name placeholders

---

## Commands

### Admin Commands

| Command | Usage | Description |
|---------|-------|-------------|
| `/dunce` | `/dunce <player> [duration] [reason]` | Dunce a player |
| `/undunce` | `/undunce <player>` | Remove dunce from a player |
| `/dunceip` | `/dunceip <player\|IP> [duration] [reason]` | Dunce player and all IP-linked accounts |
| `/undunceip` | `/undunceip <player\|IP>` | Undunce player and all IP-linked accounts |
| `/duncelookup` | `/duncelookup <player>` | View dunce details and IP info |
| `/duncealtlookup` | `/duncealtlookup <player> [depth]` | Comprehensive alt detection |
| `/dunceiplookup` | `/dunceiplookup <IP address>` | Look up all players associated with an IP |
| `/dunceiphistory` | `/dunceiphistory <player> [page]` | View a player's IP address history |
| `/dunceunlink` | `/dunceunlink <player>` | Remove player from IP tracking |
| `/clearchat` | `/clearchat` | Clear the chat |
| `/duncereload` | `/duncereload` | Reload configuration |
| `/duncemigrate` | `/duncemigrate` | Migrate from old database schema |

### Player Commands

| Command | Aliases | Description |
|---------|---------|-------------|
| `/duncechat` | `/dc` | Open the DunceChat GUI (no args) or send a message in dunce chat |
| `/dcon` | - | Show dunce chat |
| `/dcoff` | - | Hide dunce chat |

### Duration Formats

| Format | Example | Description |
|--------|---------|-------------|
| `s` | `30s` | Seconds |
| `m` | `15m` | Minutes |
| `h` | `2h` | Hours |
| `d` | `7d` | Days |
| `w` | `2w` | Weeks |
| `perm` | `perm` | Permanent (no expiry) |

---

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `duncechat.admin` | Access to all admin commands | OP |
| `duncechat.bypass` | Bypass word filter auto-dunce | OP |

---

## Configuration

### config.yml

```yaml
# Database configuration
database:
  type: h2  # Options: h2, mysql
  
  h2:
    file: "database"  # Relative to plugin folder
  
  mysql:
    host: 'localhost'
    port: 3306
    database: 'duncechat'
    username: 'your_username'
    password: 'your_password'

# Auto-migrate from old schema on startup
auto-migrate: true

# Show Dunce Chat by default to all players
visible-by-default: false

# PlaceholderAPI Integration
display-name-placeholder: ""  # e.g., "%luckperms_prefix%%player_name%"
prefix-placeholder: ""        # e.g., "%luckperms_prefix%"

# Enable automatic green text for messages starting with '>'
auto-green-text: true

# Colors for messages (MiniMessage format)
baseColor: gray
highlightColor: gold

# IP Tracking & Alt Detection
ip-tracking:
  enabled: true
  auto-dunce-on-ip-match: false  # Auto-dunce on IP match with dunced player
  notify-admins-on-alt-join: true
  alt-check-depth: 2  # 1-5, higher = more thorough

# IP Whitelist (ignored for alt detection)
ip-whitelist:
  enabled: false
  addresses:
    - "127.0.0.1"
    # - "192.168.1.0/24"  # CIDR notation supported

# IP Watchlist (alerts on join)
ip-watchlist:
  enabled: false
  notify-on-join: true
  addresses:
    # - ip: "1.2.3.4"
    #   reason: "Known VPN"
```

### words.yml

```yaml
# Words that trigger auto-dunce
disallowed-words:
  - "badword1"
  - "badword2"
```

---

## Database Schema

DunceChat uses a clean, normalized database schema (version 3):

### Tables

#### `players`
Central player information table.

| Column | Type | Description |
|--------|------|-------------|
| `uuid` | VARCHAR(36) | Player UUID (Primary Key) |
| `username` | VARCHAR(16) | Player name |
| `first_join` | TIMESTAMP | First join date |
| `last_join` | TIMESTAMP | Last join date |
| `last_quit` | TIMESTAMP | Last quit date |

#### `dunce_records`
Tracks all dunce actions with history.

| Column | Type | Description |
|--------|------|-------------|
| `id` | INT | Auto-increment ID |
| `player_uuid` | VARCHAR(36) | FK to players |
| `is_dunced` | BOOLEAN | Current dunce status |
| `reason` | TEXT | Dunce reason |
| `staff_uuid` | VARCHAR(36) | Staff who dunced (null = console/auto) |
| `dunced_at` | TIMESTAMP | When dunced |
| `expires_at` | TIMESTAMP | When dunce expires (null = permanent) |
| `undunced_at` | TIMESTAMP | When undunced |
| `trigger_message` | TEXT | Message that triggered auto-dunce |

#### `player_preferences`
Player settings for dunce chat visibility.

| Column | Type | Description |
|--------|------|-------------|
| `player_uuid` | VARCHAR(36) | FK to players (Primary Key) |
| `dunce_chat_visible` | BOOLEAN | Can see dunce chat |
| `in_dunce_chat` | BOOLEAN | Currently in dunce chat mode |

#### `player_ip_log`
Silently tracks IP associations for alt detection.

| Column | Type | Description |
|--------|------|-------------|
| `id` | INT | Auto-increment ID |
| `player_uuid` | VARCHAR(36) | FK to players |
| `ip_address` | VARCHAR(45) | IP address (supports IPv6) |
| `first_seen` | TIMESTAMP | First time seen on this IP |
| `last_seen` | TIMESTAMP | Last time seen on this IP |

#### `pending_messages`
Stores messages for offline players (e.g., dunce expiry notifications).

| Column | Type | Description |
|--------|------|-------------|
| `id` | INT | Auto-increment ID |
| `player_uuid` | VARCHAR(36) | FK to players |
| `message_key` | VARCHAR(255) | Message key to send |
| `created_at` | TIMESTAMP | When message was queued |

#### `schema_version`
Tracks database schema version for migrations.

| Column | Type | Description |
|--------|------|-------------|
| `version` | INT | Schema version number |
| `applied_at` | TIMESTAMP | When version was applied |

### Entity Relationship Diagram

```
┌──────────────┐       ┌─────────────────────┐
│   players    │───┬───│    dunce_records    │
│              │   │   │                     │
│ uuid (PK)    │   │   │ id (PK)             │
│ username     │   │   │ player_uuid (FK)    │
│ first_join   │   │   │ is_dunced           │
│ last_join    │   │   │ reason              │
│ last_quit    │   │   │ staff_uuid          │
└──────────────┘   │   │ dunced_at           │
       │           │   │ expires_at          │
       │           │   │ undunced_at         │
       │           │   │ trigger_message     │
       │           │   └─────────────────────┘
       │           │
       │           │   ┌─────────────────────┐
       │           ├───│ player_preferences  │
       │           │   │                     │
       │           │   │ player_uuid (PK,FK) │
       │           │   │ dunce_chat_visible  │
       │           │   │ in_dunce_chat       │
       │           │   └─────────────────────┘
       │           │
       │           │   ┌─────────────────────┐
       │           ├───│   player_ip_log     │
       │           │   │                     │
       │           │   │ id (PK)             │
       │           │   │ player_uuid (FK)    │
       │           │   │ ip_address          │
       │           │   │ first_seen          │
       │           │   │ last_seen           │
       │           │   └─────────────────────┘
       │           │
       │           │   ┌─────────────────────┐
       │           └───│  pending_messages   │
       │               │                     │
       │               │ id (PK)             │
       │               │ player_uuid (FK)    │
       │               │ message_key         │
       │               │ created_at          │
       │               └─────────────────────┘

All foreign keys use ON DELETE CASCADE
```

---

## Architecture

DunceChat uses a clean layered architecture with dependency injection:

```
┌─────────────────────────────────────────────────────────────┐
│                     COMMAND LAYER                            │
│  Commands receive user input and delegate to services        │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐   │
│  │  Dunce   │ │  IPDunce │ │  Toggle  │ │  AltLookup   │   │
│  │ Command  │ │ Command  │ │ Command  │ │   Command    │   │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └──────┬───────┘   │
└───────┼────────────┼────────────┼──────────────┼────────────┘
        │            │            │              │
        ▼            ▼            ▼              ▼
┌─────────────────────────────────────────────────────────────┐
│                     SERVICE LAYER                            │
│  Business logic, validation, and orchestration               │
│  ┌──────────────┐ ┌──────────────┐ ┌────────────────────┐  │
│  │ DunceService │ │PlayerService │ │ IPTrackingService  │  │
│  │              │ │              │ │                    │  │
│  │ - dunce()    │ │ - getUuid()  │ │ - handleJoin()     │  │
│  │ - undunce()  │ │ - getName()  │ │ - detectAlts()     │  │
│  │ - ipDunce()  │ │ - display()  │ │ - checkWhitelist() │  │
│  └──────┬───────┘ └──────┬───────┘ └──────────┬─────────┘  │
└─────────┼────────────────┼────────────────────┼─────────────┘
          │                │                    │
          ▼                ▼                    ▼
┌─────────────────────────────────────────────────────────────┐
│                    REPOSITORY LAYER                          │
│  Data access, SQL queries, CRUD operations                   │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐    │
│  │ DunceRepo    │ │ PlayerRepo   │ │ PlayerIPRepo     │    │
│  │              │ │              │ │                  │    │
│  │ - create()   │ │ - findByUuid │ │ - logIP()        │    │
│  │ - findActive │ │ - findByName │ │ - getByIP()      │    │
│  │ - undunce()  │ │ - save()     │ │ - getSharedIPs() │    │
│  └──────┬───────┘ └──────┬───────┘ └────────┬─────────┘    │
└─────────┼────────────────┼──────────────────┼───────────────┘
          │                │                  │
          └────────────────┼──────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    DATABASE LAYER                            │
│  Connection pooling via HikariCP                             │
│  ┌────────────────────────────────────────────────────────┐ │
│  │              DatabaseManager                            │ │
│  │  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐       │ │
│  │  │ Conn 1 │  │ Conn 2 │  │ Conn 3 │  │ Conn N │       │ │
│  │  └────────┘  └────────┘  └────────┘  └────────┘       │ │
│  └────────────────────────┬───────────────────────────────┘ │
└───────────────────────────┼─────────────────────────────────┘
                            ▼
                   ┌────────────────┐
                   │  MySQL or H2   │
                   │   Database     │
                   └────────────────┘
```

### Key Components

| Component | Purpose |
|-----------|---------|
| **Commands** | Parse user input, validate permissions, delegate to services |
| **Services** | Contain business logic, coordinate between repositories |
| **Repositories** | Handle all database operations, SQL queries |
| **DatabaseManager** | Connection pooling with HikariCP |
| **MessageManager** | Handle message formatting with MiniMessage |
| **SchemaManager** | Database migrations and version management |

---

## Data Flow

### Player Join Flow

```
Player Joins Server
        │
        ▼
┌───────────────────┐
│  PlayerService    │──► Log player to database
│  handleJoin()     │
└───────────────────┘
        │
        ▼
┌───────────────────┐
│ IPTrackingService │──► Log IP address
│  handleJoin()     │
└───────┬───────────┘
        │
        ├─► Check IP Whitelist ──► Skip if whitelisted
        │
        ├─► Check IP Watchlist ──► Alert admins if matched
        │
        └─► Check for Alts
             │
             ├─► Auto-dunce if enabled & dunced alt found
             │
             └─► Notify admins of detected alts
        │
        ▼
┌───────────────────┐
│  DunceService     │──► Send pending messages (e.g., expiry notice)
│ sendPendingMsgs() │
└───────────────────┘
```

### Dunce Command Flow

```
Admin: /dunce Player123 1d Bad behavior
                │
                ▼
        ┌───────────────┐
        │ DunceCommand  │──► Parse duration, reason
        └───────┬───────┘
                │
                ▼
        ┌───────────────┐
        │ PlayerService │──► Resolve player name to UUID
        └───────┬───────┘
                │
                ▼
        ┌───────────────┐
        │ DunceService  │──► Create dunce record
        │ duncePlayer() │──► Set preferences
        └───────┬───────┘    └─► Broadcast message
                │
                ▼
        ┌───────────────┐
        │ DunceRepo     │──► INSERT INTO dunce_records
        └───────────────┘
```

### Alt Detection Flow

```
Admin: /duncealtlookup Player123 3
                │
                ▼
        ┌────────────────────┐
        │ AltLookupCommand   │──► Parse depth parameter
        └────────┬───────────┘
                 │
                 ▼
        ┌────────────────────┐
        │ DunceService       │
        │ detectAlts()       │
        └────────┬───────────┘
                 │
                 ▼
        ┌────────────────────┐
        │ PlayerIPRepository │
        │ findAllConnected() │──► Breadth-first search
        └────────┬───────────┘
                 │
    ┌────────────┴────────────┐
    │                         │
    ▼                         ▼
┌─────────┐            ┌─────────────┐
│ Depth 1 │───────────►│ Get all IPs │
│ Player  │            │ for player  │
└─────────┘            └──────┬──────┘
                              │
                              ▼
                       ┌─────────────┐
                       │ For each IP │
                       │ get players │
                       └──────┬──────┘
                              │
                              ▼
                       ┌─────────────┐
                       │ Add to      │
                       │ result set  │
                       └──────┬──────┘
                              │
                              ▼
                       ┌─────────────┐
                       │ Repeat for  │
                       │ each depth  │
                       └─────────────┘
```

---

## PlaceholderAPI Integration

DunceChat supports PlaceholderAPI for custom display names and prefixes.

### Configuration

```yaml
# In config.yml
display-name-placeholder: "%luckperms_prefix%%player_name%"
prefix-placeholder: "%luckperms_prefix%"
```

### Supported Placeholders

Any PlaceholderAPI placeholder can be used. Common examples:

| Placeholder | Plugin | Description |
|-------------|--------|-------------|
| `%player_name%` | Built-in | Player name |
| `%luckperms_prefix%` | LuckPerms | Player's prefix |
| `%vault_prefix%` | Vault | Player's prefix via Vault |
| `%player_displayname%` | Built-in | Player's display name |

---

## Troubleshooting

### Database Connection Issues

**Error**: `No suitable driver`
- Ensure HikariCP is properly shaded in the JAR
- Check that the database type in config matches your setup

**Error**: `Unknown database`
- The plugin will auto-create the database for MySQL
- Check MySQL user has CREATE DATABASE permission

### Alt Detection Not Working

1. Check `ip-tracking.enabled: true` in config
2. Ensure player IPs are being logged (check database)
3. Verify IP is not in whitelist

### Messages Not Showing Colors

- Ensure MiniMessage format is used in `messages.properties`
- Valid format: `<red>Text</red>` or `<#FF5555>Text`

---

## For Developers

### Project Structure

```
src/main/java/gg/corn/DunceChat/
├── DunceChat.java              # Main plugin class
├── command/                    # Command handlers
│   ├── AltLookupCommand.java   # /duncealtlookup - comprehensive alt detection
│   ├── ClearChatCommand.java   # /clearchat - clear chat for all players
│   ├── DunceChatCommand.java   # /dc - send message in dunce chat or open GUI
│   ├── DunceCommand.java       # /dunce, /undunce - dunce management
│   ├── IPDunceCommand.java     # /ipdunce, /undunceip - IP-based duncing
│   ├── IPLookupCommand.java    # /dunceiplookup - IP address lookup
│   ├── LookupCommand.java      # /duncelookup - view player dunce info
│   ├── MigrateCommand.java     # /duncemigrate - schema migration
│   ├── ReloadCommand.java      # /duncereload - reload config
│   ├── ToggleCommand.java      # /dcon, /dcoff - visibility toggles
│   └── UnlinkCommand.java      # /dunceunlink - remove IP tracking
├── service/                    # Business logic layer
│   ├── DunceService.java       # Dunce operations + caching
│   ├── PlayerService.java      # Player data + PlaceholderAPI
│   ├── IPTrackingService.java  # IP tracking, alt detection, watchlist
│   └── PreferencesService.java # Player preferences + live sets
├── repository/                 # Data access layer
│   ├── DunceRepository.java    # Dunce record CRUD
│   ├── PlayerRepository.java   # Player data CRUD
│   ├── PlayerIPRepository.java # IP logging + alt queries
│   ├── PreferencesRepository.java # Player preferences CRUD
│   └── PendingMessageRepository.java # Offline message queue
├── database/                   # Database management
│   ├── DatabaseManager.java    # HikariCP connection pooling
│   └── SchemaManager.java      # Schema versioning + migrations
├── model/                      # Data models
│   ├── AltDetectionResult.java # Alt detection result DTO
│   ├── DunceRecord.java        # Dunce record entity
│   ├── Player.java             # Player entity
│   └── PlayerPreferences.java  # Preferences entity
├── listener/                   # Event listeners
│   ├── ChatListener.java       # Chat handling + word filter (optimized)
│   ├── GUIListener.java        # GUI click handlers
│   └── GreentextListener.java  # Greentext formatting
├── gui/                        # GUI components
│   └── DunceGUIBuilder.java    # Inventory GUI builder
└── util/                       # Utilities
    └── MessageManager.java     # MiniMessage formatting + i18n
```

### Building

```bash
./gradlew clean build
```

The JAR will be in `build/libs/DunceChat-X.X.jar`

### Version Management

Version is defined in `gradle.properties`:
```properties
version=2.1
```

This version is automatically applied to:
- `plugin.yml`
- `messages.properties` (help header)
- JAR filename
- Startup logs

### Dependencies

- **Paper API**: 1.21+
- **HikariCP**: Connection pooling (shaded)
- **H2 Database**: Embedded database (shaded)
- **PlaceholderAPI**: Optional soft dependency

### Performance Optimizations

DunceChat uses several optimization techniques to minimize database load:

#### In-Memory Caching

| Cache | Service | Description |
|-------|---------|-------------|
| **Dunce Cache** | `DunceService` | `ConcurrentHashMap<UUID, Optional<DunceRecord>>` caches dunce status for all active dunces |
| **Preferences Cache** | `PreferencesService` | `ConcurrentHashMap<UUID, PlayerPreferences>` caches online player preferences |
| **Visibility Sets** | `PreferencesService` | `Set<UUID>` for O(1) visibility lookups |

#### Cache Lifecycle

```
Player Join:
  ├─► Load preferences into cache (PreferencesService.loadIntoCache)
  └─► Dunce status loaded lazily on first check

Chat Message:
  ├─► isDunced() → Cache hit (no DB query)
  ├─► isDunceChatVisible() → O(1) Set lookup
  └─► isInDunceChat() → O(1) Set lookup

Player Quit:
  ├─► Invalidate preferences cache (PreferencesService.invalidateCache)
  └─► Invalidate dunce cache (DunceService.invalidateCache)
```

#### Pre-compiled Regex

Word filtering uses a single pre-compiled regex pattern instead of iterating through a word list:
- **Before**: O(n) per message where n = number of banned words
- **After**: O(1) single regex match

#### Memory Footprint Analysis

| Component | Memory Per Player | Notes |
|-----------|------------------|-------|
| PlayerPreferences cache | ~100 bytes | UUID + 2 booleans + object overhead |
| Dunce cache entry | ~200 bytes | Only for dunced players |
| UUID in visibility set | ~40 bytes | Reference only |
| **Total per online player** | ~140 bytes | Cleaned up on quit |

**Example**: 100 online players ≈ 14 KB memory overhead

The plugin is designed for **minimal memory impact**:
- Caches are scoped to online players only
- All caches are cleaned up on player quit
- Uses primitive sets where possible
- No object creation in hot paths (chat handling)

#### Why This Matters

Without caching, each chat message could trigger:
- 1 DB query per viewer for `isDunced()` check
- 1 DB query per viewer for `isDunceChatVisible()` check
- 1 DB query per viewer for `isInDunceChat()` check

On a server with 100 players and 50 chatting per minute, this could be **15,000+ DB queries/minute**. With caching, this becomes **0 DB queries** for cached data.

---

## License

This project is proprietary software. All rights reserved.

---

## Support

For issues, feature requests, or contributions, please contact the development team.

