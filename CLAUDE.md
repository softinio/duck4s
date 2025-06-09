# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Goal

Duck4s is a Scala 3 wrapper library for DuckDB, providing idiomatic Scala access to DuckDB through its Java JDBC client. The goal is to create a type-safe, functional programming-friendly interface for DuckDB operations in Scala applications.

**DuckDB Java Client Reference**: https://duckdb.org/docs/stable/clients/java

## Build Commands

This is a Scala 3 project using Mill build tool (version 0.12.14).

### Common Commands

This project supports multiple Scala versions: **3.3.6** and **3.7.0**

#### Cross-Version Commands
- **Build for all Scala versions**: `mill __.compile`
- **Build for specific Scala version**: `mill 'duck4s[3.3.6].compile'` or `mill 'duck4s[3.7.0].compile'`
- **Run tests for all Scala versions**: `mill __.test`
- **Run tests for specific Scala version**: `mill 'duck4s[3.3.6].test'` or `mill 'duck4s[3.7.0].test'`
- **Run specific test**: `mill 'duck4s[3.7.0].test' com.softinio.duck4s.MySuite`

#### Documentation Commands
- **Generate API documentation with static site**: `mill 'duck4s[3.7.0].docJar'`
- **Generated site location**: `out/duck4s/3.7.0/docJar.dest/javadoc/index.html`

#### Development Commands
- **Format code**: `mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources`
- **Check formatting**: `mill mill.scalalib.scalafmt.ScalafmtModule/checkFormatAll __.sources`
- **Clean build**: `mill clean`

#### Publishing Commands
- **Publish locally for all Scala versions**: `mill __.publishLocal`
- **Publish locally for specific Scala version**: `mill 'duck4s[3.3.6].publishLocal'` or `mill 'duck4s[3.7.0].publishLocal'`
- **Published artifacts location**: `~/.ivy2/local/com.softinio/duck4s_3.3.6/` and `~/.ivy2/local/com.softinio/duck4s_3.7.0/`

### Development Environment
The project uses Nix flake for reproducible development environments. To enter the dev shell:
- `nix develop` (if you have Nix with flakes enabled)

## Project Architecture

This is a Scala 3 wrapper library for DuckDB with the following structure:
- **Package**: `com.softinio.duck4s` (with types in `com.softinio.duck4s.algebra`)
- **Main source**: `duck4s/src/` - Library code providing Scala 3 wrapper for DuckDB JDBC
  - `Connection.scala` - Main connection handling and query execution
  - `algebra/` - Core types and data structures
    - `Batch.scala` - Batch operations and type classes
    - `BatchResult.scala` - Batch execution result type
    - `Config.scala` - Configuration types (ConnectionMode, DuckDBConfig)
    - `DuckDBError.scala` - Error type hierarchy
    - `PreparedStatement.scala` - Prepared statement wrapper
    - `ResultSet.scala` - Result set wrapper
- **Tests**: `duck4s/test/src/` - Tests using MUnit framework
- **Build definition**: `build.mill` - Mill build configuration defining the project as a ScalaModule

The project uses:
- **Scala 3.3.6 and 3.7.0** with modern syntax features (optional braces, colon syntax)
- **MUnit 1.1.1** for testing
- **Scalafmt 3.9.7** configured for Scala 3 dialect
- **DuckDB JDBC Driver 1.1.3** for database connectivity
- **Scala 3 Scaladoc** for comprehensive API documentation generation with:
  - Snippet compiler for validating code examples
  - External mappings for Scala and Java standard library documentation
  - Static site generation with custom layouts

### Key Design Considerations
- Provide type-safe Scala 3 APIs for DuckDB operations
- Support functional programming patterns (immutability, error handling with Either/Try)
- Leverage Scala 3 features like extension methods, given instances, and opaque types where appropriate
- Maintain compatibility with DuckDB's JDBC interface while providing idiomatic Scala abstractions

## Documentation

The project includes comprehensive scaladoc documentation with a static website:

### Static Site Structure
- **duck4s/docs/**: Static site source files following Scala 3 scaladoc static site guide
  - **_docs/**: Markdown content files
    - `index.md` - Main homepage with logo
    - `getting-started.md` - Getting started guide
  - **_assets/**: Static assets
    - **images/**: Logo and other images
      - `duck4s_logo.jpeg` - Project logo
      - `favicon.ico` - Site favicon

### Documentation Features
- **Package documentation**: Overview and examples for main package and algebra package
- **Class documentation**: Detailed descriptions with @param, @return, @example, @see tags
- **Method documentation**: Complete parameter and return value descriptions
- **Type class documentation**: Explanations of BatchBinder and ParameterBinder patterns
- **Cross-references**: Linked documentation between related types
- **Examples**: Practical usage examples throughout the API
- **Static site integration**: Homepage, getting started guide, and direct links to API docs

Generate the complete documentation website with `mill 'duck4s[3.7.0].docJar'`.

## Publishing and Release Process

### Required GitHub Secrets
To enable automatic publishing to Maven Central, configure these secrets in your GitHub repository:
- `MILL_PGP_SECRET_BASE64`: Base64-encoded PGP private key for signing artifacts
- `MILL_PGP_PASSPHRASE`: Passphrase for the PGP key
- `MILL_SONATYPE_USERNAME`: Sonatype (Maven Central) username
- `MILL_SONATYPE_PASSWORD`: Sonatype (Maven Central) password

### Release Process

#### Using the release script (recommended):
```bash
./scripts/release.sh 0.1.0
```

This script will:
1. Validate you're on the main branch with no uncommitted changes
2. Create an annotated git tag (v0.1.0)
3. Push the tag to GitHub, triggering the release workflow

#### Manual process:
```bash
git tag -a v0.1.0 -m "Release 0.1.0"
git push origin v0.1.0
```

The release workflow will then:
1. Run CI tests
2. Publish all Scala version artifacts to Maven Central
3. Create a GitHub release with the artifacts
4. Trigger documentation deployment to GitHub Pages

### Documentation Deployment

#### Automatic deployment
The documentation is automatically published to GitHub Pages after a successful release. The docs workflow:
1. Waits for the Release workflow to complete successfully
2. Generates the Scala documentation using Mill
3. Deploys to GitHub Pages at https://softinio.github.io/duck4s/

#### Manual deployment
To update documentation without creating a new release:
```bash
./scripts/publish-docs.sh          # Uses version from latest git tag
./scripts/publish-docs.sh 0.1.0   # Uses specified version
```

This script will:
1. Generate documentation with the specified version (or latest tag version)
2. Push directly to the gh-pages branch
3. Make it available at https://softinio.github.io/duck4s/

This is useful for:
- Fixing documentation typos without a new release
- Updating examples or documentation content
- Regenerating docs with the same version after documentation improvements

**Note**: You must enable GitHub Pages in your repository settings:
- Go to Settings → Pages
- Source: Deploy from a branch
- Branch: gh-pages
- The documentation will be available after the first deployment

## Important Notes
- The project follows standard Scala conventions with separate source and test directories
- Mill builds are defined in `build.mill` using Scala syntax
- The `.scalafmt.conf` is configured to use modern Scala 3 syntax features
- All public APIs include comprehensive scaladoc documentation for generated documentation websites
- The build includes conditional scalac options (e.g., `-Xkind-projector:underscores` for Scala 3.7)
- Documentation links use the `[[algebra.TypeName]]` format for cross-references to types in the algebra package
