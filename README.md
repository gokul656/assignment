# Rogers Account Management API

CRUD API for a customer Account Management service, keyed by email (one account per email).
On create/update, country + postal code are enriched via https://api.zippopotam.us to resolve
Place, State, Longitude and Latitude, which are persisted alongside the account.

## Run

```
./mvnw spring-boot:run
```

Starts on `http://localhost:8080`. On startup, `DemoDataSeeder` creates ~9 sample accounts
(across US/AL, US/CT, US/NY, DE, ES, FR, including one pre-set to `INACTIVE`) so every endpoint
has data to exercise immediately — account ids/PINs are printed in the startup log.

## Test

```
./mvnw test
```

84 unit tests across:
- `AccountServiceTest` — all business rules (create/update/delete/status/counts), including email
  conflict/case-insensitivity, location-refresh-only-when-needed (verified via Mockito
  `verify(times(1))` on the zippopotam client), not-found paths, and the `UNKNOWN` fallback when an
  account has no resolved location.
- `AccountRepositoryTest` — case-insensitive email uniqueness/lookup, email reindexing, snapshot
  immutability of `findAll()`.
- `IdGeneratorTest` — account ID / security PIN format.
- `ZippopotamClientTest` — success mapping, 404 → `PostalLookupException`, empty/null response
  bodies, generic upstream failures, malformed coordinate strings (mocks the `RestClient` fluent
  chain directly).
- `GlobalExceptionHandlerTest` — each `@ExceptionHandler` method exercised directly, including the
  Jackson-cause field-name extraction path.
- `AccountControllerTest` (`@WebMvcTest` + mocked `AccountService`) — HTTP-layer wiring and
  real Bean Validation enforcement.

## API contract & codegen

[`src/main/resources/openapi.yaml`](src/main/resources/openapi.yaml) is the source of truth for
the API contract (bonus task b). It's wired into the Maven build via
`openapi-generator-maven-plugin` (`generate-sources` phase), which generates, into
`target/generated-sources/openapi` (not checked in):

- `com.example.demo.account.api.AccountsApi` — a Spring MVC interface with all `@RequestMapping`/
  `@Valid` annotations derived from the spec. `AccountController` implements this interface
  directly — there is no hand-written `@PostMapping`/`@GetMapping` left in the controller.
- `com.example.demo.account.model.*` — request/response model classes (`CreateAccountRequest`,
  `AccountResponse`, `CountryCode`, `AccountStatusValue`, etc.), including Jakarta Bean Validation
  annotations derived from the spec's `pattern`/`minimum`/`maximum`/`required` constraints.

Regenerate with `./mvnw generate-sources` after editing `openapi.yaml` — IDEs should pick up
`target/generated-sources/openapi` as a source root automatically (the plugin registers it).

## API

| Method | Path                              | Purpose                                              |
|--------|-----------------------------------|-------------------------------------------------------|
| POST   | `/api/accounts`                   | Create account                                        |
| PUT    | `/api/accounts/{accountId}`       | Update an Active account                               |
| DELETE | `/api/accounts/{accountId}?securityPin=1234` | Delete an Inactive account                  |
| GET    | `/api/accounts?accountId=...` or `?email=...` | Retrieve one account                        |
| GET    | `/api/accounts/counts?country=US` | Account counts grouped by State → Place (bonus-adjacent) |
| PATCH  | `/api/accounts/{accountId}/status`| Change account status (bonus task a)                   |

### Example

```
curl -X POST localhost:8080/api/accounts -H 'Content-Type: application/json' -d '{
  "name": "Alice", "email": "alice@example.com", "country": "US", "postalCode": "35203", "age": 30
}'
# -> {"accountId":"Y547PL","status":"ACTIVE","securityPin":"9348"}

curl "localhost:8080/api/accounts?email=alice@example.com"
# -> {"accountId":"Y547PL","email":"alice@example.com","status":"ACTIVE","age":30,
#     "location":{"place":"Birmingham","state":"AL","country":"US","postalCode":"35203",
#                  "longitude":-86.8066,"latitude":33.521}}
```

## Assumptions

- **Storage**: in-memory (`ConcurrentHashMap`-backed repository) per the assignment's note that
  "use of an in-memory database is fine" — data does not survive a restart.
- **Account ID**: 6-character alphanumeric (A-Z, 0-9), randomly generated and checked for
  uniqueness against existing ids.
- **Security PIN**: 4-digit numeric, randomly generated at creation, returned only in the create
  (and bonus status-change) responses; required to authorize `DELETE`.
- **POST status handling**: a `status` field may be omitted or set to `"Requested"`; any other
  value is rejected with 400. Regardless of what's submitted, the persisted account is always
  created as `ACTIVE`, per the spec ("Allow only 'Requested' status on POST... On creation all
  Accounts are defaulted to 'Active' status").
- **Name**: alphanumeric only (no spaces/punctuation), 1–20 characters, mandatory.
- **Country**: restricted to `US`, `DE`, `ES`, `FR` (case-insensitive input, stored uppercase);
  same allow-list is used for the `/counts` endpoint.
- **Postal code**: exactly 5 digits, mandatory on create.
- **Update (`PUT`)**: only allowed while the account's current status is `ACTIVE`; allowed fields
  are Name, Email, Country, Postal Code, Age, Status, matching the spec. The zippopotam lookup is
  re-run only if Country and/or Postal Code actually changed.
- **Delete (`DELETE`)**: only allowed while the account's current status is `INACTIVE`, and
  requires the correct Security PIN (passed as a query param rather than a DELETE body, since
  request bodies on DELETE are not universally supported by HTTP clients/proxies).
- **Retrieve one (`GET /api/accounts`)**: accepts either `accountId` or `email` as a query param;
  if both are supplied, `accountId` takes precedence; at least one is required.
- **Retrieve counts**: computed over all accounts (regardless of status) matching the given
  country; states and places are sorted alphabetically for deterministic output, matching the
  nesting shown in the spec's example JSON.
- **Bonus status-change endpoint**: `PATCH /api/accounts/{id}/status` sets the status directly to
  any of `REQUESTED`/`ACTIVE`/`INACTIVE` without the "must be Active" restriction that applies to
  the general `PUT` update, since its purpose is explicitly to transition between all three states
  (e.g. activating a Requested account, or deactivating an Active one ahead of delete).
- **Swagger/codegen**: `openapi.yaml` is hand-authored (rather than reverse-generated by a
  `springdoc` runtime dependency, since a compatible `springdoc-openapi` release for Spring Boot 4 /
  Spring Framework 7 wasn't available to resolve in this environment) and is the contract that
  drives codegen — see "API contract & codegen" above. Enum fields (`country`, `status`) use
  top-level named schemas rather than inline enums, because `openapi-generator`'s inline/nested
  enums silently fall back to `null` on an unrecognized value instead of throwing, which would have
  let an invalid status slip past validation instead of returning 400.
- **Error format**: all failures return a consistent JSON body
  `{timestamp, status, error, message, fieldErrors}` via a global `@RestControllerAdvice`, including
  enum-deserialization failures from the generated models (e.g. `country: "CA"`), which are mapped
  back onto `fieldErrors.<field>` the same as Bean Validation failures.
