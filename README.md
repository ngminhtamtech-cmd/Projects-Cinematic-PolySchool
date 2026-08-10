# Projects Cinematic PolySchool — CineBook

CineBook is a full-stack cinema ticketing and operations platform developed as
a graduation project at FPT Polytechnic. It combines a Java web application,
a SQL Server database, a JSP interface, and a Next.js customer-facing frontend.

The system covers the ticket lifecycle from movie discovery and seat holding to
payment, QR ticket validation, cancellation, refund review, and operational
reporting. Role and cinema-scope controls support customers, staff, managers,
and system administrators.

## Main features

- Browse films, cinemas, showtimes, formats, promotions, and cinema content.
- Select seats with temporary holds and concurrency protection.
- Create orders, process simulated payments, issue QR tickets, and generate
  invoices.
- View order history, cancel eligible orders, request refunds, and appeal refund
  decisions.
- Manage customer profiles, reviews, notifications, membership tiers, loyalty
  points, and vouchers.
- Support staff counter sales and signed QR ticket check-in.
- Manage films, cinemas, rooms, seats, showtimes, combos, promotions, users,
  approvals, and system settings.
- Enforce cinema-level access for staff and managers and retain administrative
  audit records.
- Expose a versioned REST surface for the Next.js frontend while the complete JSP
  booking flow remains available.

## Architecture

```text
Next.js / JSP
      |
Servlet controllers and REST API
      |
Application services
      |
JDBC DAOs + HikariCP
      |
Microsoft SQL Server
```

The Java backend follows `Servlet -> Service -> JDBC DAO -> SQL Server`. It does
not use Spring or JPA. The project currently has two frontend surfaces:

- JSP, JSTL, CSS, and browser JavaScript under `src/main/webapp/`.
- Next.js App Router, React, TypeScript, and Tailwind CSS under `web/`.

## Technology stack

| Area | Technology |
| --- | --- |
| Backend | Java 17, Jakarta EE 8 APIs, `javax.servlet`, JSP/JSTL |
| Runtime | Apache Tomcat 9 |
| Database | Microsoft SQL Server, JDBC, HikariCP |
| Frontend | Next.js 16, React 19, TypeScript, Tailwind CSS 4 |
| Build | Maven, npm |
| Tests | JUnit 5, Maven Surefire/Failsafe, Node test runner |
| Libraries | Jackson, ZXing, OpenPDF, JavaMail, BCrypt |

> Tomcat 9 is required. The application imports `javax.servlet.*`; Tomcat 10 and
> later use the incompatible `jakarta.servlet.*` namespace.

## Prerequisites

Install these tools before cloning the repository:

- Git.
- JDK 17 or newer; the project compiles for Java 17.
- Apache Maven 3.9+ or Maven bundled with NetBeans.
- Apache Tomcat 9.x. Do not use Tomcat 10+.
- Microsoft SQL Server 2019 or newer with SQL authentication enabled.
- Microsoft ODBC Driver and `sqlcmd` utilities for SQL Server.
- Node.js 22.6+ and npm.

Confirm that the tools are available:

```powershell
git --version
java -version
mvn -version
sqlcmd -?
node --version
npm --version
```

If Maven is not on `PATH`, set `CINEBOOK_MVN` to the full path of `mvn.cmd`.
The included `scripts/build.cmd` also checks `MAVEN_HOME`, `M2_HOME`, `PATH`, and
common NetBeans locations.

## Clone the repository

```powershell
git clone https://github.com/ngminhtamtech-cmd/Projects-Cinematic-PolySchool.git
Set-Location Projects-Cinematic-PolySchool
```

These local configuration files are intentionally not tracked:

- `db.properties`
- `db.test.properties`
- `web/.env.local`

Never commit the generated copies.

## 1. Create and initialize SQL Server

The expected application database is `CineBookDB`. `database/schema.sql` is only
the base schema; every migration must run before the seed file.

### Create the database

Set the SQL Server password for the current PowerShell session and create the
database:

```powershell
$env:SQLCMDPASSWORD = '<YOUR_SQL_SERVER_PASSWORD>'

sqlcmd -S localhost -U sa -C -I -b -d master -Q `
  'IF DB_ID(N''CineBookDB'') IS NULL CREATE DATABASE [CineBookDB];'
```

The commands use `-C` for a local development certificate, `-I` for quoted
identifiers, `-b` for a failing exit code, and `-f 65001` for UTF-8 SQL files.

### Run the migration chain

Run the base schema, ordered pre-fix migrations, all numbered `fixNN` scripts,
and the main seed file:

```powershell
$migrationFiles = @(
  'database/schema.sql'
  'database/alter_cinema_films.sql'
  'database/alter_cinemas_add_banner.sql'
  'database/alter_rooms_status.sql'
  'database/alter_seats_add_maintenance.sql'
  'database/alter_seats_price_surcharge.sql'
  'database/alter_showtimes_format_version.sql'
  'database/alter_users_add_staff_role.sql'
  'database/alter_users_comments_appeals.sql'
  'database/alter_users_promotions_loyalty.sql'
  'database/migration_update_cinemas.sql'
  'database/migration_update_films.sql'
  'database/migration_v2_films_and_cinemas.sql'
  'database/alter_and_seed_showtimes_ui.sql'
)

$migrationFiles += Get-ChildItem 'database' -Filter 'fix*.sql' |
  Where-Object Name -ne 'fix00_backup_and_testdb.sql' |
  Sort-Object Name |
  ForEach-Object FullName

$migrationFiles += (Resolve-Path 'database/seed.sql').Path

foreach ($file in $migrationFiles) {
  Write-Host ('Running {0}' -f $file)
  sqlcmd -S localhost -U sa -C -I -b -f 65001 -d CineBookDB -i $file
  if ($LASTEXITCODE -ne 0) { throw ('Migration failed: {0}' -f $file) }
}
```

`database/more_seeds.sql`, `database/seed_fpt_test_data.sql`, and
`database/seed_staff_checkin_demo.sql` are optional demonstration datasets.
Read them before applying them to an existing database.

Clear the password from the shell when it is no longer needed:

```powershell
Remove-Item Env:SQLCMDPASSWORD
```

## 2. Configure the Java backend

### Local IDE configuration

For Maven tests or IDE runs, create a local project configuration:

```powershell
Copy-Item 'db.properties.example' 'db.properties'
```

Edit `db.properties` and replace at least these values:

```properties
db.url=jdbc:sqlserver://localhost:1433;databaseName=CineBookDB;encrypt=true;trustServerCertificate=true
db.username=sa
db.password=YOUR_SQL_SERVER_PASSWORD
ticket.hmac.secret=REPLACE_WITH_AT_LEAST_32_RANDOM_CHARACTERS
mail.mode=logfile
```

Use a long, random ticket HMAC secret that is different for each deployment.

### Tomcat runtime configuration

The WAR deliberately excludes `db.properties`. For Tomcat, place the real file
outside the deployed application:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:CATALINA_HOME = 'C:\tools\apache-tomcat-9'
$env:CATALINA_BASE = $env:CATALINA_HOME

$cinebookConfigDir = Join-Path $env:CATALINA_BASE 'conf\cinebook'
New-Item -ItemType Directory -Force $cinebookConfigDir | Out-Null
Copy-Item 'db.properties.example' (Join-Path $cinebookConfigDir 'db.properties')
```

Edit `%CATALINA_BASE%\conf\cinebook\db.properties` with the real database
credentials and a new HMAC secret. The backend resolves configuration in this
order:

1. JVM option `-Dcinebook.db.config=<absolute-path>`.
2. Environment variable `CINEBOOK_CONFIG` or `CINEBOOK_DB_CONFIG`.
3. `%CATALINA_BASE%\conf\cinebook\db.properties`.
4. A project/classpath fallback for local development only.

Uploads default to `%CATALINA_BASE%\cinebook-uploads`. Ensure Tomcat can write to
that directory. Override it with `CINEBOOK_UPLOAD_DIR` or
`-Dcinebook.upload.dir=<absolute-path>` when needed.

### Email mode

Keep `mail.mode=logfile` for local development. Messages are written to
`%CATALINA_BASE%\logs\cinebook-mail.log`.

For SMTP, add `mail.smtp.host`, `mail.smtp.port`, `mail.smtp.username`,
`mail.smtp.password`, `mail.smtp.starttls`, and `mail.from` to the external
Tomcat configuration. Then update the database setting because
`SystemSettings` takes precedence over the file:

```powershell
sqlcmd -S localhost -U sa -C -I -b -d CineBookDB -Q `
  'UPDATE SystemSettings SET SettingValue=''smtp'' WHERE SettingKey=''mail.mode'';'
```

Keep SMTP credentials only in the external configuration file.

## 3. Build and deploy the backend

Build the WAR from the repository root:

```powershell
.\scripts\build.cmd clean package
```

The output is `target/Website-ban-ve-xem-phim-1.0-SNAPSHOT.war`. Deploy it with
the stable context name expected by the frontend:

```powershell
$war = 'target\Website-ban-ve-xem-phim-1.0-SNAPSHOT.war'
$deployWar = Join-Path $env:CATALINA_HOME 'webapps\Website-ban-ve-xem-phim.war'
Copy-Item $war $deployWar -Force

& (Join-Path $env:CATALINA_HOME 'bin\startup.bat')
```

Useful URLs:

- Application: `http://localhost:8080/Website-ban-ve-xem-phim/home`
- Health check: `http://localhost:8080/Website-ban-ve-xem-phim/api/v1/health`

Stop Tomcat with:

```powershell
& (Join-Path $env:CATALINA_HOME 'bin\shutdown.bat')
```

Tomcat logs are under `%CATALINA_BASE%\logs`.

## 4. Configure and run Next.js

The backend must use the `Website-ban-ve-xem-phim` context shown above.

```powershell
Set-Location web
Copy-Item '.env.example' '.env.local'
npm ci
npm run dev
```

The default `web/.env.example` contains:

```dotenv
CINEBOOK_API_BASE=http://localhost:8080/Website-ban-ve-xem-phim/api/v1
NEXT_PUBLIC_ASSET_BASE=http://localhost:8080/Website-ban-ve-xem-phim
NEXT_PUBLIC_JSP_BASE=http://localhost:8080/Website-ban-ve-xem-phim
```

Open `http://localhost:3000`. Next.js proxies API requests to Tomcat and hands
the seat-booking flow to JSP, where session, seat-hold, and payment state remain.

For a production frontend build:

```powershell
npm run build
npm start
Set-Location ..
```

## Verification and tests

Run the fast verification suite from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File '.\scripts\verify-fast.ps1'
```

It runs backend unit tests, Checkstyle, WAR packaging, `npm ci`, frontend unit
tests, and a production Next.js build.

Integration tests create a uniquely named temporary database and drop it after
the run. They require a local SQL Server password:

```powershell
$env:CINEBOOK_DB_PASSWORD = '<YOUR_SQL_SERVER_PASSWORD>'
.\scripts\run-integration-tests.ps1 -Server localhost -User sa -Maven mvn
Remove-Item Env:CINEBOOK_DB_PASSWORD
```

Do not point integration tests at the real `CineBookDB` database.

## Project structure

```text
database/                   SQL Server schema, migrations, and seed data
scripts/                    Build, database, verification, and operations scripts
src/main/java/              Controllers, services, DAOs, models, and filters
src/main/resources/         Runtime resources without credentials
src/main/webapp/            JSP views and browser assets
src/test/                   Unit, contract, and integration tests
web/                        Next.js and React frontend
pom.xml                     Maven WAR build
db.properties.example       Safe backend configuration template
db.test.properties.example  Safe test configuration template
```

## Troubleshooting

- **Tomcat starts but the app fails:** confirm Tomcat 9 is used and inspect
  `%CATALINA_BASE%\logs\localhost*.log` for schema or JSP errors.
- **Database login fails:** enable SQL Server TCP/IP and SQL authentication,
  confirm port `1433`, and verify the JDBC URL and credentials.
- **A table or column is missing:** run the complete migration chain;
  `schema.sql` alone is insufficient.
- **Next.js cannot load movies or images:** verify Tomcat is running and all
  values in `web/.env.local` use the exact deployed context path.
- **Email is not sent:** local mode writes to `cinebook-mail.log`; SMTP requires
  external credentials and `SystemSettings.mail.mode=smtp`.
- **Maven is not found:** add Maven to `PATH`, set `MAVEN_HOME`, or set
  `CINEBOOK_MVN` to a valid `mvn.cmd`.

## Security notes

- Real database, email, and signing credentials are ignored by Git.
- Replace every example secret before deployment.
- Back up an existing database before applying migrations.
- Use a dedicated database account with minimum permissions outside development.

## Academic context

This public repository contains a graduation project intended for learning,
demonstration, and continued development.
