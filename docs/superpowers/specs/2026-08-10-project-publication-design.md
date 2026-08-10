# CineBook Public Repository Publication Design

## Objective

Publish the CineBook graduation project as a new public GitHub repository at
`ngminhtamtech-cmd/Projects-Cinematic-PolySchool` without exposing credentials,
personal files, generated artifacts, or unrelated data from the surrounding
filesystem.

## Repository Boundary

The repository root is the nested source directory containing `pom.xml`, `src/`,
`database/`, `scripts/`, and `web/`. Git must be initialized only in that
directory. The parent Git repository is out of scope because its root includes
the user's home directory.

The outer graduation report files (`.docx` and `.pdf`) are excluded. Local
credentials, runtime data, dependency directories, IDE state, and build output
are excluded through `.gitignore` and a pre-commit file review.

## Documentation Cleanup

The final repository keeps two project Markdown documents:

- `CLAUDE.md`, preserving the existing engineering reference.
- `README.md`, replaced with an English public-facing guide.

Other project Markdown files are removed. The vendored jsQR attribution is not
discarded: `jsQR.NOTICE.md` is renamed to `jsQR.NOTICE.txt`, while the existing
Apache-2.0 license text remains beside the bundle.

This temporary design specification is removed in the final cleanup commit so
the published tree satisfies the two-document Markdown requirement.

## README Structure

The README will contain:

1. Project overview and graduation-project context.
2. Main capabilities for customers, staff, managers, and administrators.
3. Architecture and technology stack.
4. Prerequisites with compatible versions: JDK 17, Maven, Tomcat 9, SQL Server,
   SQL command-line utilities, Node.js, and npm.
5. Exact clone and initial setup commands.
6. SQL Server database creation, schema, migration, and seed guidance based on
   the repository's scripts.
7. Backend configuration using `db.properties.example`, including safe secrets,
   JDBC settings, mail mode, and the external Tomcat configuration location.
8. WAR build and Tomcat 9 deployment with the expected context path.
9. Next.js configuration from `web/.env.example`, dependency installation, and
   development/production commands.
10. Unit tests, frontend tests, build verification, common URLs, and concise
    troubleshooting notes.

The README must not contain real passwords, tokens, machine-specific absolute
paths, or claims that are not supported by the source configuration.

## GitHub Publication Flow

Create a clean `main` branch in the source directory, commit the reviewed
project snapshot, create a public repository named
`Projects-Cinematic-PolySchool` in the `ngminhtamtech-cmd` account, add it as
`origin`, and push `main` with upstream tracking. No force push is used.

If the repository name already exists or authentication does not authorize
repository creation, stop before overwriting or publishing elsewhere and report
the exact blocker.

## Validation

Before publication:

- Confirm the Git top-level directory is exactly the source directory.
- Confirm ignored credentials and generated artifacts are absent from the Git
  index.
- Scan staged file names and contents for common credential patterns.
- Confirm the only final `*.md` files are `CLAUDE.md` and `README.md`.
- Run backend unit tests and package/checkstyle verification.
- Run frontend unit tests and a production build.
- Inspect the final commit summary and remote URL before pushing.

After publication, verify the remote `main` branch and public repository URL.

## Failure Handling

Build or test failures are reported with their exact failing command and are not
hidden. GitHub authentication failures, repository-name conflicts, and network
errors halt publication without force pushing, deleting remote content, or
switching to a different account.
