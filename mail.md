Subject: Rogers Account Management API — Assignment Submission for Review

Hi [Name],

I've completed the Account Management API assignment and wanted to share it for your review. A quick summary below, along with the repo link.

Repository: https://github.com/gokul656/assignment

Tech stack:
- Java 25, Spring Boot 4.0.8 / Spring Framework 7
- OpenAPI Generator (contract-first — openapi.yaml drives generated controllers/DTOs) + springdoc-openapi (Swagger UI)
- Spring Data Redis (Lettuce) + Spring Cache — caches postal code lookups, with graceful fallback if Redis is unavailable
- Spring Security Crypto (BCrypt) — PIN hashing
- Logback (custom masking converter for sensitive log fields)
- Docker (Redis), dist.cmd for running the packaged jar

What's included:
- Full CRUD on customer accounts, keyed by email, with country/postal-code enrichment via the Zippopotam API
- Account status lifecycle (Requested/Active/Inactive) with PIN-protected updates, deletion, and status changes
- Consistent, sanitized error responses (no internal stack traces or type names leaked to clients)
- Two branches showing alternate security designs for comparison:
    - feature/pin-verification-token-flow — verify-once + short-lived token exchange
    - feature/aspect-pin — simpler AOP-based PIN-in-body validation
- README with setup instructions, API examples, a sequence diagram for the PIN flow, and documented assumptions

Please review and let me know your feedback or any questions.

Thanks,
[Your name]