# 🧼 Scrubbr

Clean your files before you share them. Upload an image or PDF, see the hidden metadata and sensitive data it carries, and download a cleaned copy.

Files are processed in memory and never written to disk.

## What it does

- **Images (JPEG/PNG):** reveals EXIF/GPS metadata (location, device, timestamps) and strips it.
- **PDF:** reveals document metadata and scans the text for emails, phone numbers, credit-card numbers (Luhn-checked), SSNs and IPs — then wipes the metadata and redacts the matches.
- Detects the real file type from its bytes, not the extension.

## Stack

Java 21 · Spring Boot 3 · Apache PDFBox · metadata-extractor · vanilla HTML/CSS/JS · JUnit 5

## Run

```bash
mvn spring-boot:run        # http://localhost:8080
```

Or build a jar:

```bash
mvn clean package
java -jar target/scrubbr-1.0.0.jar
```

## API

| Method | Endpoint | Returns |
|--------|----------|---------|
| `POST` | `/api/analyze` | JSON report of findings |
| `POST` | `/api/clean` | the cleaned file |

```bash
curl -F "file=@photo.jpg" http://localhost:8080/api/analyze
curl -F "file=@photo.jpg" http://localhost:8080/api/clean -o photo-scrubbed.jpg
```

