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

91 unit tests across:
- `AccountServiceTest` — all business rules (create/update/delete/status/counts), including email
  conflict/case-insensitivity, location-refresh-only-when-needed (verified via Mockito
  `verify(times(1))` on the zippopotam client), not-found paths, and the `UNKNOWN` fallback when an
  account has no resolved location.
- `AccountRepositoryTest` — case-insensitive email uniqueness/lookup, email reindexing, snapshot
  immutability of `findAll()`.
- `IdGeneratorTest` — account ID format.
- `ZippopotamClientTest` — success mapping, 404 → `PostalLookupException`, empty/null response
  bodies, generic upstream failures, malformed coordinate strings (mocks the `RestClient` fluent
  chain directly).
- `GlobalExceptionHandlerTest` — each `@ExceptionHandler` method exercised directly, including the
  Jackson-cause field-name extraction path.
- `PinAttemptTrackerTest` — lockout triggers after 5 consecutive failures, resets on success,
  tracked independently per account.
- `VerificationTokenStoreTest` — issue/consume round-trip, single-use (second consume fails),
  unknown/null tokens.
- `PinVerificationServiceTest` — correct PIN issues a redeemable token, wrong PIN fails without
  issuing one, 5 consecutive failures lock out a 6th attempt (even with the correct PIN) until the
  lockout window passes, success resets the failure count.
- `VerificationTokenInterceptorTest` — the `@RequiresVerificationToken` interceptor in isolation:
  non-annotated methods pass through without touching the token store, missing/blank header,
  unknown/expired token, token issued for a different account, valid token.
- `AccountControllerTest` (`@WebMvcTest` + mocked `AccountService`/`PinVerificationService`) —
  HTTP-layer wiring and real Bean Validation enforcement. Also mocks `VerificationTokenStore`
  since `@WebMvcTest` auto-detects `VerificationTokenInterceptor` as a `HandlerInterceptor` bean
  and wires it into the slice.

## API contract & codegen

[`src/main/resources/openapi.yaml`](src/main/resources/openapi.yaml) is the source of truth for
the API contract (bonus task b). It's wired into the Maven build via
`openapi-generator-maven-plugin` (`generate-sources` phase), which generates, into
`target/generated-sources/openapi` (not checked in):

- `com.example.demo.controller.AccountsApi` — a Spring MVC interface with all `@RequestMapping`/
  `@Valid` annotations derived from the spec. `AccountController` implements this interface
  directly — there is no hand-written `@PostMapping`/`@GetMapping` left in the controller.
- `com.example.demo.dto.*` — request/response model classes (`CreateAccountRequest`,
  `AccountResponse`, `CountryCode`, `AccountStatusValue`, etc.), including Jakarta Bean Validation
  annotations derived from the spec's `pattern`/`minimum`/`maximum`/`required` constraints.

Regenerate with `./mvnw generate-sources` after editing `openapi.yaml` — IDEs should pick up
`target/generated-sources/openapi` as a source root automatically (the plugin registers it).

## Package layout

Organized by technical layer rather than by feature:

- `controller` — `AccountController` (implements the generated `AccountsApi`)
- `service` — `AccountService` (business rules), `ZippopotamClient` + its supporting records
  (`ZippopotamResponse`, `PostalLocation`)
- `repository` — `AccountRepository` (in-memory store)
- `domain` — `Account`, `AccountStatus`, `Location` (internal persisted model, distinct from the
  generated `dto` request/response classes)
- `dto` — generated request/response models (see above)
- `util` — `IdGenerator`
- `config` — `RestClientConfig`, `DemoDataSeeder`, `SecurityConfig` (`PasswordEncoder` bean), `WebConfig`
  (registers `VerificationTokenInterceptor`)
- `security` — `@RequiresVerificationToken` + `VerificationTokenInterceptor` (enforces it),
  `VerificationTokenStore` (issues/consumes tokens), `PinAttemptTracker` (brute-force lockout) —
  see "Security PIN & verification token flow" below
- `exception` — `ApiException` hierarchy, `Constants`, `GlobalExceptionHandler`

## Security PIN & verification token flow

The security PIN is never stored in plaintext: `AccountService.createAccount` hashes it with
`BCryptPasswordEncoder` before persisting (`Account.securityPinHash`), and only returns the
plaintext value once, in the create response.

`PUT`, `DELETE`, and the bonus status-change endpoint don't take the PIN directly. Instead:

1. **`POST /api/accounts/{accountId}/verify-pin`** — `PinVerificationService` checks the PIN
   against the stored hash and, on success, issues a single-use verification token via
   `VerificationTokenStore` (a cryptographically random, `SecureRandom`-backed value, opaque to
   the caller) that expires after 300 seconds. On failure, `PinAttemptTracker` records the miss;
   after 5 consecutive failures the account is locked out for 15 minutes — further attempts
   (even with the correct PIN) are rejected with 403 without even checking the PIN, until the
   lockout window passes. A success resets the failure count.
2. **`PUT`/`DELETE`/`PATCH .../status`** — each caller must send the token from step 1 as an
   `X-Verification-Token` header. `@RequiresVerificationToken` marks the three controller methods;
   `VerificationTokenInterceptor.preHandle()` checks for that annotation via `HandlerMethod`
   reflection, then — before Spring's own argument binding/validation runs, so this is the only
   point the raw header/path-variable values are available this way — pulls the header and the
   `accountId` path variable directly off the `HttpServletRequest`, consumes the token via the
   store (single-use: a captured token can never be replayed, successfully or not), and confirms
   it was issued for this same account.
3. On success the interceptor returns `true` and the request proceeds to the controller/service as
   normal (status preconditions, email uniqueness, zippopotam lookup). On failure it throws
   `InvalidVerificationTokenException` (401: missing, expired, already used, or issued for a
   different account) or, from the verify-pin endpoint itself, `InvalidSecurityPinException` (403,
   wrong PIN) / `TooManyAttemptsException` (403, locked out) — all handled by the existing
   `GlobalExceptionHandler`, so the error response shape is identical to any other failure.

This two-step design (verify once, reuse a short-lived token across the one subsequent mutating
call) was chosen over sending the raw PIN on every `PUT`/`DELETE`/status call: the PIN itself never
has to be retransmitted for each mutating request, and a captured token is both time-boxed and
single-use rather than being a long-lived credential. The header-based interceptor approach (over
a Bean Validation constraint) was necessary either way: the request DTOs are OpenAPI-generated, so
there's no hand-written class to attach a custom `@Constraint` to, and the account-ID/token pairing
has to be checked against server-side state, which a stateless field-level annotation can't do.

**Assumption**: the bonus status-change endpoint (`PATCH .../status`) is token-protected too, since
it mutates an existing account just like `PUT` — treating it differently would undermine the same
rule it's otherwise enforcing. If a fully open status endpoint is actually wanted, that's a
one-line change (drop `@RequiresVerificationToken` from `AccountController.changeStatus`).

## API

| Method | Path                              | Purpose                                              | Token needed? |
|--------|-----------------------------------|-------------------------------------------------------|---------------|
| POST   | `/api/accounts`                   | Create account                                        | No            |
| GET    | `/api/accounts?accountId=...` or `?email=...` | Retrieve one account                        | No            |
| GET    | `/api/accounts/counts?country=US` | Account counts grouped by State → Place (bonus-adjacent) | No         |
| POST   | `/api/accounts/{accountId}/verify-pin` | Verify the PIN, returns a verification token     | No (takes the PIN) |
| PUT    | `/api/accounts/{accountId}`       | Update an Active account                               | **Yes**       |
| DELETE | `/api/accounts/{accountId}`       | Delete an Inactive account                             | **Yes**       |
| PATCH  | `/api/accounts/{accountId}/status`| Change account status (bonus task a)                   | **Yes**       |

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

curl -X POST localhost:8080/api/accounts/Y547PL/verify-pin -H 'Content-Type: application/json' \
  -d '{"securityPin": "9348"}'
# -> {"verificationToken":"kX9fQb3n...","expiresInSeconds":300}

curl -X PUT localhost:8080/api/accounts/Y547PL -H 'X-Verification-Token: kX9fQb3n...' \
  -H 'Content-Type: application/json' -d '{"age": 31}'
# -> 200 (updated account) - reusing the same token again now returns 401 (single-use)
```

## Assumptions

- **Storage**: in-memory (`ConcurrentHashMap`-backed repository) per the assignment's note that
  "use of an in-memory database is fine" — data does not survive a restart.
- **Account ID**: 6-character alphanumeric (A-Z, 0-9), randomly generated and checked for
  uniqueness against existing ids.
- **Security PIN**: 4-digit numeric, randomly generated at creation, returned only in the create
  response; stored as a BCrypt hash, never in plaintext. Exchanged for a verification token via
  `POST /verify-pin` (see "Security PIN & verification token flow" above) rather than sent
  directly on every mutating call — a deliberate extension beyond the spec's literal text, which
  only lists a Security PIN for `DELETE` and doesn't describe a token exchange at all.
- **POST status handling**: a `status` field may be omitted or set to `"Requested"`; any other
  value is rejected with 400. Regardless of what's submitted, the persisted account is always
  created as `ACTIVE`, per the spec ("Allow only 'Requested' status on POST... On creation all
  Accounts are defaulted to 'Active' status").
- **Name**: alphanumeric only (no spaces/punctuation), 1–20 characters, mandatory.
- **Country**: restricted to `US`, `DE`, `ES`, `FR` (case-insensitive input, stored uppercase);
  same allow-list is used for the `/counts` endpoint.
- **Postal code**: exactly 5 digits, mandatory on create.
- **Update (`PUT`)**: only allowed while the account's current status is `ACTIVE`; requires a
  valid `X-Verification-Token` header (see "Security PIN & verification token flow" above).
  Allowed fields are Name, Email, Country, Postal Code, Age, Status, matching the spec. The
  zippopotam lookup is re-run only if Country and/or Postal Code actually changed.
- **Delete (`DELETE`)**: only allowed while the account's current status is `INACTIVE`, and
  requires a valid `X-Verification-Token` header, obtained the same way as for `PUT`.
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
  back onto `fieldErrors.<field>` the same as Bean Validation failures. Per-field messages are
  unwrapped to the innermost cause (e.g. `"Unexpected value 'CA'"`) rather than Jackson's raw
  multi-line diagnostic dump (type name, byte offset, reference chain), and unhandled (500) errors
  return a generic message — the real exception and stack trace are logged server-side only, never
  echoed to the client.
