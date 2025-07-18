
![tests](https://github.com/softinio/duck4s/actions/workflows/ci.yml/badge.svg) | ![release](https://github.com/softinio/duck4s/actions/workflows/release.yml/badge.svg)  | ![documentation](https://github.com/softinio/duck4s/actions/workflows/docs.yml/badge.svg) | [![duck4s Scala version support](https://index.scala-lang.org/softinio/duck4s/duck4s/latest.svg)](https://index.scala-lang.org/softinio/duck4s/duck4s)

# duck4s

<p align="center">
  <img src="duck4s/docs/_assets/images/duck4s_logo.jpeg" alt="Duck4s Logo" width="400">
</p>

A modern, type-safe Scala 3 wrapper library for [DuckDB](https://duckdb.org/) that provides idiomatic, functional programming-friendly access to DuckDB's analytical database capabilities through its Java JDBC client.

## Overview

Duck4s makes working with DuckDB in Scala applications a pleasant experience by:
- Providing type-safe APIs that leverage Scala 3's advanced type system
- Supporting functional programming patterns with `Either[DuckDBError, T]` error handling
- Offering automatic resource management with `withConnection`, `withPreparedStatement`, and `withBatch` methods
- Maintaining full compatibility with DuckDB's JDBC interface while providing idiomatic Scala abstractions

## Features

- 🦆 **Type Safety** - Comprehensive error handling with `Either[DuckDBError, T]` types
- 🔧 **Resource Management** - Automatic resource cleanup with `withConnection`, `withPreparedStatement`, and `withBatch` methods
- 🚀 **Functional Programming** - Designed for composition with for-comprehensions and functional patterns
- 📊 **Modern Scala 3** - Utilizes Scala 3 features like extension methods, given instances, and braceless syntax
- 🔄 **Batch Operations** - Efficient type-safe batch processing with type classes
- 💼 **Transaction Support** - First-class transaction management with automatic rollback
- 📱 **LTS and Latest Scala Version Support** - Supports Scala 3.3.6 (LTS) and 3.7.0

## Getting Started

### Installation

#### SBT

Add duck4s to your `build.sbt`:

```scala
libraryDependencies += "com.softinio" %% "duck4s" % "0.1.1"
```

#### Mill

Add duck4s to your `build.mill`:

```scala
def ivyDeps = Agg(
  ivy"com.softinio::duck4s::0.1.1"
)
```

### Prerequisites

- Scala 3.3.6 or 3.7.0
- Java 17 or higher
- Mill build tool (or use the provided Nix development environment)

### Development Setup

This project uses Nix for reproducible development environments. To get started:

```bash
# Enter the development shell (requires Nix with flakes)
nix develop

# Build for all Scala versions
mill __.compile

# Build for specific Scala version
mill 'duck4s[3.7.0].compile'

# Run tests
mill __.test

# Generate documentation
mill 'duck4s[3.7.0].docJar'

# Format code
mill mill.scalalib.scalafmt.ScalafmtModule/reformatAll __.sources
```

### Building from Source

```bash
# Clone the repository
git clone https://github.com/softinio/duck4s.git
cd duck4s

# Build the project
mill __.compile

# Run tests
mill __.test
```

## Quick Example

```scala
import com.softinio.duck4s.*
import com.softinio.duck4s.algebra.*

// Simple query execution
val result = DuckDBConnection.withConnection() { conn =>
  for
    _ <- conn.executeUpdate("CREATE TABLE users (id INTEGER, name VARCHAR)")
    _ <- conn.executeUpdate("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')")
    rs <- conn.executeQuery("SELECT * FROM users ORDER BY id")
  yield
    while rs.next() do
      println(s"${rs.getInt("id")}: ${rs.getString("name")}")
    rs.close()
}

result match
  case Right(_) => println("Query executed successfully")
  case Left(error) => println(s"Error: $error")
```

## Documentation

- [Getting Started Guide](https://softinio.github.io/duck4s/docs/getting-started.html) - Learn the basics of duck4s
- [API Documentation](https://softinio.github.io/duck4s/) - Complete API reference

## Contributing

**GitHub Issues** are disabled to encourage direct community contribution. When you encounter bugs or documentation issues, please contribute fixes through Pull Requests instead.

**How to contribute:** [Open a PR](https://github.com/softinio/duck4s/pulls) with your solution, draft changes, or a test reproducing the issue. We'll collaborate from there to refine and merge improvements.

This approach creates faster fixes while building a stronger, community-driven project where everyone benefits from shared contributions.

## Using AI Help to use or contribute to this project

This project is optimized and setup for you to use [Claude Code](https://www.anthropic.com/claude-code) as your AI programming agent. It is recommended to use Claude Code.


## License

Duck4s is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

## Acknowledgments

- [DuckDB](https://duckdb.org/) for creating an excellent analytical database
- The Scala community for continuous innovation in functional programming
