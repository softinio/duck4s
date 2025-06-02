# Duck4s

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
libraryDependencies += "com.softinio" %% "duck4s" % "0.1.0"
```

#### Mill

Add duck4s to your `build.sc`:

```scala
def ivyDeps = Agg(
  ivy"com.softinio::duck4s::0.1.0"
)
```

### Prerequisites

- Scala 3.3.6 or 3.7.0
- Java 11 or higher
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
- [Examples](https://github.com/softinio/duck4s/tree/main/examples) - Practical usage examples

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

Duck4s is licensed under the Apache License 2.0. See [LICENSE](LICENSE) for details.

## Acknowledgments

- [DuckDB](https://duckdb.org/) for creating an excellent analytical database
- The Scala community for continuous innovation in functional programming
