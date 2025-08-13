# Spring Document Updater CLI

A Spring Shell-based CLI application that helps you maintain and update Spring-related documents by automatically fetching the latest news from RSS feeds and managing document sections.

## Features

- 📰 Fetch latest Spring release blogs from RSS feeds
- 📅 **Automatically integrate Spring release calendar events**
- 📝 Generate document templates with proper sections
- 🔄 Update news sections automatically
- 📋 Manage enterprise releases from calendar
- ⏰ Track upcoming releases with dates
- 🎯 Update demo sections
- 👀 Preview content before updating
- 💾 Configurable file names and sources
- ⚡ Full document updates with one command

## Prerequisites

- Java 17 or later
- Maven 3.6 or later

## Building the Application

```bash
mvn clean package
```

## Running the Application

```bash
java -jar target/spring-doc-updater-1.0.0.jar
```

Or during development:

```bash
mvn spring-boot:run
```

## Available Commands

### Document Management

| Command | Description | Example |
|---------|-------------|---------|
| `create` | Create a new document with template | `create my-update.md` |
| `show` | Display current document content | `show my-update.md` |
| `help-doc` | Show detailed help information | `help-doc` |

### News Management

| Command | Description | Example |
|---------|-------------|---------|
| `update-news` | Update news section from RSS feed | `update-news my-doc.md https://spring.io/blog/category/releases.atom 10` |
| `preview-news` | Preview latest news without updating | `preview-news https://spring.io/blog/category/releases.atom 5` |

### Calendar Integration

| Command | Description | Example |
|---------|-------------|---------|
| `update-releases` | Add recent releases from calendar | `update-releases my-doc.md https://calendar.spring.io/ical 14` |
| `update-upcoming` | Update upcoming releases section | `update-upcoming my-doc.md https://calendar.spring.io/ical 60` |
| `preview-calendar` | Preview calendar releases | `preview-calendar https://calendar.spring.io/ical 7 30` |
| `full-update` | Update everything at once | `full-update my-doc.md` |

### Release Management

| Command | Description | Example |
|---------|-------------|---------|
| `add-release` | Add an enterprise release | `add-release my-doc.md "August 1" "Spring Boot 3.3.3"` |

### Demo Management

| Command | Description | Example |
|---------|-------------|---------|
| `update-demo` | Update the demo section | `update-demo my-doc.md "[Spring Cloud Demo](https://github.com/example/demo)"` |

## Usage Examples

### Quick Start

1. Start the CLI:
   ```bash
   java -jar target/spring-doc-updater-1.0.0.jar
   ```

2. Create a new document:
   ```
   shell:>create weekly-update.md
   ```

3. Update with latest Spring news:
   ```
   shell:>update-news weekly-update.md
   ```

4. View the updated document:
   ```
   shell:>show weekly-update.md
   ```

### Calendar-Powered Workflow

1. **Full automated update:**
   ```
   shell:>full-update weekly-update.md
   ```

2. **Update recent releases from calendar:**
   ```
   shell:>update-releases weekly-update.md https://calendar.spring.io/ical 14
   ```

3. **Update upcoming releases:**
   ```
   shell:>update-upcoming weekly-update.md https://calendar.spring.io/ical 60
   ```

4. **Preview calendar data:**
   ```
   shell:>preview-calendar https://calendar.spring.io/ical 7 30
   ```

### Advanced Usage

Preview news before updating:
```
shell:>preview-news https://spring.io/blog/category/releases.atom 5
```

Add enterprise releases:
```
shell:>add-release weekly-update.md "August 5" "Spring Framework 6.2.1"
shell:>add-release weekly-update.md "August 5" "Spring Security 6.4.2"
```

Update demo section:
```
shell:>update-demo weekly-update.md "[Spring AI Demo](https://github.com/spring-projects/spring-ai-examples)"
```

### Working with Different RSS Feeds

The application supports any valid RSS/Atom feed:

```
shell:>update-news my-doc.md https://spring.io/blog.atom 15
shell:>preview-news https://spring.io/blog/category/engineering.atom 3
```

## Document Template

The application generates documents with the following structure:

```markdown
# [Current Date]

News:
- [Fetched from RSS feed]

Enterprise Releases:
- [Manually added releases]

Releases coming soon:
- Micrometer
- Micrometer Tracing
- Reactor
- Reactor Core
- Reactor Netty
- Reactor Pool
- Spring Framework
- Spring LDAP
- Spring Data

Demo:
[Demo links and descriptions]
```

## Configuration

The application can be configured via `application.properties`:

- Timeout settings for web requests
- Logging levels
- Default RSS feed URLs

## Error Handling

The CLI provides helpful error messages for common issues:
- Network connectivity problems
- Invalid RSS feed URLs
- File system permissions
- Malformed RSS content

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License.