# Newsletter CLI

A Spring Shell-based CLI application for generating and managing newsletter documents. Automatically fetches content from multiple sources including RSS feeds, Spring release calendar, YouTube channels, and GitHub repositories.

## Features

- 📰 **RSS/Atom Feed Integration** - Fetch latest Spring release blogs and news
- 📅 **Spring Release Calendar** - Automatically integrate recent and upcoming releases  
- 🎥 **YouTube Integration** - Pull latest videos from Spring channels
- 🚀 **GitHub Demo Discovery** - Automatically find repositories ending in '-demo'
- 📝 **Document Templates** - Generate structured markdown documents
- 👀 **Preview Mode** - Preview content before updating documents
- 💾 **Flexible Configuration** - Configurable file names, URLs, and limits
- ⚡ **Batch Updates** - Full document updates with one command
- 🧹 **Smart Formatting** - Automatic spacing cleanup and consistent formatting

## Prerequisites

- Java 21 or later
- Maven 3.6 or later

## Building the Application

```bash
mvn clean package
```

## Running the Application

```bash
java -jar target/newsletter-0.0.1-SNAPSHOT.jar
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
| `update-demo` | Update the demo section manually | `update-demo my-doc.md "[Spring Cloud Demo](https://github.com/example/demo)"` |
| `update-github-demos` | Auto-update with GitHub '-demo' repos | `update-github-demos my-doc.md` |
| `preview-github-demos` | Preview available demo repositories | `preview-github-demos` |

### YouTube Management

| Command | Description | Example |
|---------|-------------|---------|
| `update-youtube` | Update YouTube section with latest videos | `update-youtube my-doc.md 10` |
| `preview-youtube` | Preview latest YouTube videos | `preview-youtube 5` |

## Usage Examples

### Quick Start

1. Start the CLI:
   ```bash
   java -jar target/newsletter-0.0.1-SNAPSHOT.jar
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

Preview content before updating:
```
shell:>preview-news https://spring.io/blog/category/releases.atom 5
shell:>preview-youtube 10
shell:>preview-github-demos
shell:>preview-calendar https://calendar.spring.io/ical 7 30
```

Update specific sections:
```
shell:>update-youtube weekly-update.md 15
shell:>update-github-demos weekly-update.md
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

## News:
- [Fetched from RSS feed]

## Recent Enterprise Releases:
- [Calendar-based releases grouped by date]

## Releases coming soon:
- [Upcoming calendar events or default list]

## YouTube:
- [Latest videos from Spring channels]

## Demos:
- [GitHub demo repositories]
```

## Configuration

The application can be configured via `application.properties`:

- **Interactive/Non-interactive modes**: `spring.shell.interactive.enabled=true`
- **Web request timeouts**: `spring.webflux.timeout.connect=10s`, `spring.webflux.timeout.read=30s`
- **Logging levels**: `logging.level.root=OFF` for clean CLI output
- **Application settings**: Banner mode, web application type disabled for CLI

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