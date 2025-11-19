ng# Database Configuration Guide

## Overview

DunceChat now supports two database backends:
- **H2** (embedded database - default, recommended for single-server setups)
- **MySQL** (external database - recommended for multi-server/network setups)

## H2 Database (Default)

### Features
- **No setup required** - Database file is created automatically
- **File-based** - Data stored in local files
- **MySQL compatibility mode** - Uses MySQL syntax
- **Perfect for single servers** - No external database needed

### Configuration

```yaml
database:
  type: h2
  h2:
    file: "plugins/DunceChat/database"
```

The database will be created as:
- `plugins/DunceChat/database.mv.db` (main database file)
- `plugins/DunceChat/database.trace.db` (trace/log file, if debugging enabled)

### Benefits
- Zero external dependencies
- Fast for single-server setups
- Automatic backups by copying the `.mv.db` file
- No network latency

### Drawbacks
- Cannot be shared across multiple servers
- Slightly less performant with very large datasets (10,000+ players)

## MySQL Database

### Features
- **Network-based** - Can be shared across multiple servers
- **Scalable** - Better performance with large datasets
- **Industry standard** - Well-documented and supported

### Configuration

```yaml
database:
  type: mysql
  mysql:
    host: 'localhost'
    port: 3306
    database: 'detox_db'
    username: 'your_username'
    password: 'your_password'
```

### Requirements
- MySQL 5.7+ or MariaDB 10.2+
- Network access to MySQL server
- Database and user created with proper permissions

### Setup Steps

1. **Create Database**
   ```sql
   CREATE DATABASE detox_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. **Create User**
   ```sql
   CREATE USER 'duncechat'@'%' IDENTIFIED BY 'secure_password';
   GRANT ALL PRIVILEGES ON detox_db.* TO 'duncechat'@'%';
   FLUSH PRIVILEGES;
   ```

3. **Update config.yml**
   ```yaml
   database:
     type: mysql
     mysql:
       host: 'your-mysql-host'
       port: 3306
       database: 'detox_db'
       username: 'duncechat'
       password: 'secure_password'
   ```

## Troubleshooting

### Error: "Database connection pool is not initialized!"

**Cause**: The database failed to initialize, usually due to:
1. Incorrect configuration
2. Missing database driver (now included in plugin JAR)
3. Network/permission issues (MySQL only)

**Solution for H2**:
- Ensure the plugin has write permissions to `plugins/DunceChat/` folder
- Check that `database.type` is set to `h2` in config.yml
- Delete `plugins/DunceChat/database.mv.db` and restart (will recreate)

**Solution for MySQL**:
- Verify database credentials are correct
- Ensure MySQL server is running and accessible
- Check firewall rules allow connection to MySQL port
- Verify user has correct permissions on the database

### Error: "NoClassDefFoundError: org/h2/Driver"

**Cause**: H2 driver not included in plugin JAR (old build)

**Solution**: Rebuild with latest build.gradle that includes H2 shading

### H2 Database Corrupted

**Solution**:
1. Stop the server
2. Backup the database: Copy `plugins/DunceChat/database.mv.db`
3. Delete the corrupted file
4. Restart server (new database will be created)
5. Restore from backup if needed

## Migration Between Database Types

### H2 to MySQL

1. **Export H2 data** (using H2 console or script)
2. **Configure MySQL** in config.yml
3. **Restart server** - Schema will be created automatically
4. **Import data** into MySQL tables

### MySQL to H2

1. **Export MySQL data** (using mysqldump or similar)
2. **Change config.yml** to use H2
3. **Restart server** - H2 database will be created
4. **Import data** using H2 console or SQL script

## Performance Tuning

### H2 Settings
The H2 configuration is optimized by default. The database runs in MySQL compatibility mode.

### MySQL Settings

Connection pool settings (in DatabaseManager.java):
- **Maximum Pool Size**: 10 connections
- **Minimum Idle**: 2 connections
- **Connection Timeout**: 30 seconds
- **Idle Timeout**: 10 minutes
- **Max Lifetime**: 30 minutes

To adjust these, modify the `DatabaseManager.initialize()` method.

## Included Dependencies

The plugin includes these libraries (shaded and relocated):
- **HikariCP 5.1.0** - Connection pooling
- **H2 Database 2.2.224** - Embedded database
- **SLF4J API 2.0.9** - Logging framework

All dependencies are relocated to `gg.corn.DunceChat.libs.*` to avoid conflicts.

## Technical Details

### File Locations

- **H2 Database**: `plugins/DunceChat/database.mv.db`
- **Configuration**: `plugins/DunceChat/config.yml`
- **Plugin JAR**: `plugins/DunceChat-1.0-SNAPSHOT.jar`

### JDBC URLs

- **H2**: `jdbc:h2:plugins/DunceChat/database;MODE=MySQL`
- **MySQL**: `jdbc:mysql://host:port/database?useSSL=false&allowPublicKeyRetrieval=true`

### Schema

Both database types use the same schema:
- `schema_version` - Tracks schema version
- `players` - Player information
- `dunce_records` - Dunce history and active dunces
- `player_preferences` - Player settings (visibility, chat mode)

## Support

If you encounter issues:
1. Check server console logs for detailed error messages
2. Verify configuration in `config.yml`
3. Test database connectivity independently
4. Review this documentation for common issues
5. Create an issue on GitHub with logs and configuration

