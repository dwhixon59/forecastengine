# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

ForecastEngine is a personal finance application (Java 21 + Maven + MySQL) that tracks
transactions, budgets, and generates cash-flow forecasts. Package root: `com.hixon.financialApp`.

## Commands

- Build: `mvn clean install`
- Run all tests: `mvn test`
- Run a single test class: `mvn test -Dtest=BudgetControllerTest`
- Run a single test method: `mvn test -Dtest=BudgetControllerTest#methodName`
- Run the app: launch `Main` with goal name(s) as program arguments, e.g. `dailyUpdate`,
  `manageData`, `importRegisterTransactions`, `createForecast`, `renderLongTermForecast`
- Config setup: copy `src/main/resources/db.properties.example` to `db.properties` (git-ignored,
  never commit — contains DB credentials)

Notes: Java 21 with `--enable-preview` (configured in surefire); Lombok annotation processing is
wired up in `pom.xml`. Some legacy tests under `src/test` have `main()` methods and hit a real
database rather than running under JUnit.

## Architecture: MVC with a swappable View layer

The single hard rule: **controllers never interact with the user directly.** All I/O goes through
the `ViewInt` interface (`view/base/ViewInt.java`). Controllers call `view.say(...)`,
`view.getYesOrNo(...)`, `view.selectByNameFromList(...)`, etc. This keeps one controller codebase
working across multiple front-ends: `view/cmdLine` (active), `view/excel`, `view/csv`, `view/text`,
`view/spreadsheetXml`.

- **Entry point:** `Main.java` loads `db.properties`, builds a `DatabaseConnectionManager`, then
  `new MainController(user, mgr, new ViewCmdline(), new fileBasedNotificationService())`.
- **Goals dispatch:** `MainController.run(String[] goals)` is a big `switch` over goal strings
  passed as program args. Add a new feature = add a `case` here.
- **Session state:** `SessionController` lazily loads register/budget/forecast via
  `getRegisterBudgetForecast()`. Sub-controllers take a `SessionController` in their constructor.
- **Notifications:** async report delivery via `notification/async/{email,file,textmsg}` behind
  `NotificationServiceInt`.

## Persistence: hand-written SQL, no ORM

- Entities extend `model/entity/Entity` and implement `EntityInt`. Each entity supplies its own
  SQL strings: `getInsertQuery()`, `getUpdateByIdQuery()`, `getDeleteByIdQuery()`,
  `getInsertOnDuplicateUpdateQuery()`. Base `save(SaveMethod)` dispatches on a **dirty flag** —
  no-op unless `isDirty()`. Call `setDirty(true)` after mutating fields.
- Get the connection only via `Utility.getDbConnection()` (auto-reconnects on MySQL timeout).
  Never cache a `Connection`.
- MySQL UUIDs use `uuid_to_bin('...')` / `bin_to_uuid(...)`. Escape user strings with
  `Utility.escapeSqlString(...)` (queries are string-concatenated, not prepared statements).

## Global singletons via `Utility`

`Utility` holds static app-wide state, set once in `MainController`'s constructor:
`Utility.setConnectionManager(...)`, `Utility.setUser(...)`, `Utility.setView(...)`. Model/utility
code reaches the current view through `Utility.getView()`. `Utility` is also the home for all
date/currency formatting (`calendarDateToStringDate`, `formatDollarAmount`, `isEqualCurrency` —
compare currency with the `CURRENCY_COMPARISON_THRESHOLD`, never `==`).

## Control flow via exceptions (project-specific)

User "escape hatches" are modeled as checked exceptions, not return codes. View input methods
throw `CancelException`, `QuitException`, `SkipException` when the user chooses those options
(gated by `isCancelAllowed`/`isQuitAllowed`/`isSkipAllowed` boolean params). Controllers propagate
these; `MainController.run` catches `QuitException`/`CancelException` at the top level and closes
the DB connection. Use the `ViewInt` constants (`ALLOW_CANCEL`, `DO_NOT_ALLOW_SKIP`, ...) for
readability instead of bare booleans.

## Bank imports: Factory + Strategy

Institution-specific parsing lives in `model/financialinstitution` (`WellsFargoBank`,
`BarclaysBank`, `GenericBank`) built by `FinancialInstitutionFactory`. Import file formats use
Strategy classes in `controller`: `QfxImportStrategy`, `OfxImportStrategy`, `QifImportStrategy`
(QFX/OFX via `ofx4j`). Excel/CSV via Apache POI + commons-csv.

## Conventions

- Custom checked exceptions per module (`EntityException`, `RegisterException`, `BudgetException`,
  `ForecastException`).
- Dates are `java.util.Calendar` throughout (not `java.time`); convert with `Utility` helpers.
- Source control: do not commit unless asked — the user tests first, then requests a commit with
  a concise message. After each major change, remind the user to commit.

## Testing
- Unit tests are under `src/test/java`. Use `mvn test` to run all tests, or `mvn test -Dtest=ClassName`
  to run a specific class. Some legacy tests have `main()` methods and hit a real database; prefer
  JUnit tests for new code.
- Always create unit tests for new features or bug fixes. Use mocks/stubs for external dependencies when possible.

## Related docs

The repo root has numerous design docs worth checking before large changes in their area:
`TRANSACTION_IMPORT_ALGORITHM.md`, `AUTO_MATCHING_ALGORITHM.md`, `PLAID_INTEGRATION_DESIGN.md`,
`REDUCE_IMPORT_QUESTIONS_DESIGN.md`, `IMPORT_SUMMARY_RECATEGORIZE_DESIGN.md`,
`DATABASE_RECONNECT_DESIGN.md`, `PERIOD_HOWOCCURS_RELATIONSHIP.md`,
`QFX_BALANCE_INTEGRATION.md`, `Composable_Qualifiers_Summary.md`,
`SearchQualifierProcessor_Usage_Guide.md`.
