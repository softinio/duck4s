---
title: Getting Started
---

# Getting Started with Duck4s

This guide will help you get started with duck4s, a Scala 3 wrapper for DuckDB that provides type-safe, functional access to analytical database operations.

## Installation

### SBT

Add duck4s to your `build.sbt`:

```sbt
// Core library
libraryDependencies += "com.softinio" %% "duck4s" % "0.1.4"

// Optional: cats-effect integration (includes fs2)
libraryDependencies += "com.softinio" %% "duck4s-cats-effect" % "0.1.4"
```

### Mill

Add duck4s to your `build.mill`:

```scala sc:nocompile
// Core library
def ivyDeps = Agg(
  ivy"com.softinio::duck4s::0.1.4"
)

// Optional: cats-effect integration (includes fs2)
def ivyDeps = Agg(
  ivy"com.softinio::duck4s::0.1.4",
  ivy"com.softinio::duck4s-cats-effect::0.1.4"
)
```

## Basic Usage

### Imports

Start by importing the necessary packages:

```scala sc-name:Imp.scala
import com.softinio.duck4s.*
import com.softinio.duck4s.algebra.*
```

### Creating Connections

Duck4s supports both in-memory and persistent databases:

```scala sc-compile-with:Imp.scala
// In-memory database (default)
val inMemoryResult = DuckDBConnection.withConnection() { conn =>
  // Your database operations here
  Right("Success")
}

// Persistent database
val config = DuckDBConfig.persistent("/path/to/database.db")
val persistentResult = DuckDBConnection.withConnection(config) { conn =>
  // Your database operations here
  Right("Success")
}
```

### Basic Queries

Execute simple queries and updates:

```scala sc-compile-with:Imp.scala
val result = DuckDBConnection.withConnection() { conn =>
  for
    // Create a table
    _ <- conn.executeUpdate("""
      CREATE TABLE users (
        id INTEGER PRIMARY KEY,
        name VARCHAR,
        age INTEGER
      )
    """)

    // Insert data
    insertCount <- conn.executeUpdate("""
      INSERT INTO users VALUES
        (1, 'Alice', 25),
        (2, 'Bob', 30),
        (3, 'Charlie', 35)
    """)

    // Query data
    rs <- conn.executeQuery("SELECT * FROM users WHERE age > 25")
  yield
    println(s"Inserted $insertCount rows")
    while rs.next() do
      val id = rs.getInt("id")
      val name = rs.getString("name")
      val age = rs.getInt("age")
      println(s"User $id: $name, age $age")
    rs.close()
}

result match
  case Right(_) => println("Query executed successfully")
  case Left(error) => println(s"Error: $error")
```

### Prepared Statements

Use prepared statements for parameterized queries:

```scala sc-compile-with:Imp.scala
val result = DuckDBConnection.withConnection() { conn =>
  for
    _ <- conn.executeUpdate("CREATE TABLE products (id INTEGER, name VARCHAR, price DOUBLE)")

    // Use prepared statement for safe parameter binding
    insertResult <- conn.withPreparedStatement("INSERT INTO products VALUES (?, ?, ?)") { stmt =>
      for
        _ <- stmt.setInt(1, 1)
        _ <- stmt.setString(2, "Laptop")
        _ <- stmt.setDouble(3, 999.99)
        count <- stmt.executeUpdate()
      yield count
    }

    // Query with parameters
    queryResult <- conn.withPreparedStatement("SELECT * FROM products WHERE price > ?") { stmt =>
      for
        _ <- stmt.setDouble(1, 500.0)
        rs <- stmt.executeQuery()
      yield
        while rs.next() do
          println(s"${rs.getString("name")}: $$${rs.getDouble("price")}")
        rs.close()
    }
  yield (insertResult, queryResult)
}
```

### Batch Operations

Efficiently insert multiple rows using batch operations:

```scala sc-compile-with:Imp.scala
val result = DuckDBConnection.withConnection() { conn =>
  for
    _ <- conn.executeUpdate("CREATE TABLE employees (id INTEGER, name VARCHAR, salary DOUBLE)")

    batchResult <- conn.withBatch("INSERT INTO employees VALUES (?, ?, ?)") { batch =>
      for
        _ <- batch.addBatch(
          (1, "Alice", 75000.0),
          (2, "Bob", 80000.0),
          (3, "Charlie", 85000.0)
        )
        result <- batch.executeBatch()
      yield result
    }
  yield batchResult
}

result match
  case Right(batchResult) =>
    println(s"Successfully inserted ${batchResult.successCount} rows")
    println(s"Failed operations: ${batchResult.failureCount}")
  case Left(error) =>
    println(s"Batch operation failed: $error")
```

### Supported Parameter Types

Duck4s provides first-class support for all common DuckDB column types. The following types can be used with prepared statements (`setXxx` methods) and batch operations (`addBatch` tuples) without any manual conversion:

| Scala / Java type | Setter method | DuckDB column type |
|---|---|---|
| `Int` | `setInt` | `INTEGER` |
| `Long` | `setLong` | `BIGINT` |
| `Double` | `setDouble` | `DOUBLE` |
| `Float` | `setFloat` | `FLOAT` |
| `Boolean` | `setBoolean` | `BOOLEAN` |
| `String` | `setString` | `VARCHAR` |
| `BigDecimal` | `setBigDecimal` | `DECIMAL` |
| `java.sql.Date` | `setDate` | `DATE` |
| `java.sql.Timestamp` | `setTimestamp` | `TIMESTAMP` |
| `java.sql.Types.*` | `setNull` | any (NULL) |
| `java.util.UUID` | `setObject` | `UUID` |
| `java.time.LocalDate` | `setObject` | `DATE` |
| `java.time.LocalDateTime` | `setObject` | `TIMESTAMP` |
| `java.time.OffsetDateTime` | `setObject` | `TIMESTAMPTZ` |
| `Array[Byte]` | `setBytes` | `BLOB` |
| `Option[T]` | (any of the above) | nullable variant |

The same types are available when reading results from a `DuckDBResultSet` via the corresponding `getXxx` methods. For BLOB columns, use `getBytes(columnLabel)` which internally wraps the DuckDB-specific `getBlob` implementation.

```scala sc-compile-with:Imp.scala
val result = DuckDBConnection.withConnection() { conn =>
  for
    _ <- conn.executeUpdate("""
      CREATE TABLE events (
        id       INTEGER,
        name     VARCHAR,
        score    FLOAT,
        price    DECIMAL(10,2),
        recorded DATE,
        created  TIMESTAMP,
        uid      UUID,
        payload  BLOB
      )
    """)

    ts    = java.sql.Timestamp.valueOf("2024-06-15 10:30:00")
    date  = java.sql.Date.valueOf("2024-06-15")
    uuid  = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    bytes = "hello".getBytes("UTF-8")

    _ <- conn.withPreparedStatement("INSERT INTO events VALUES (?, ?, ?, ?, ?, ?, ?, ?)") { stmt =>
      for
        _ <- stmt.setInt(1, 1)
        _ <- stmt.setString(2, "launch")
        _ <- stmt.setFloat(3, 9.5f)
        _ <- stmt.setBigDecimal(4, BigDecimal("19.99"))
        _ <- stmt.setDate(5, date)
        _ <- stmt.setTimestamp(6, ts)
        _ <- stmt.setObject(7, uuid)
        _ <- stmt.setBytes(8, bytes)
        count <- stmt.executeUpdate()
      yield count
    }

    rs <- conn.executeQuery("SELECT * FROM events WHERE id = 1")
  yield
    assert(rs.next())
    val retrievedUuid  = rs.getObject("uid", classOf[java.util.UUID])
    val retrievedBytes = rs.getBytes("payload")
    rs.close()
}
```

#### Batch operations with tuples

`addBatch` accepts tuples of up to 6 elements, with any combination of the supported types:

```scala sc-compile-with:Imp.scala
val result = DuckDBConnection.withConnection() { conn =>
  for
    _ <- conn.executeUpdate("CREATE TABLE readings (id INTEGER, ts TIMESTAMP, uid UUID, val FLOAT, dec DECIMAL(10,2), data BLOB)")

    batchResult <- conn.withBatch("INSERT INTO readings VALUES (?, ?, ?, ?, ?, ?)") { batch =>
      val ts1   = java.sql.Timestamp.valueOf("2024-01-01 00:00:00")
      val uuid1 = java.util.UUID.randomUUID()
      for
        _ <- batch.addBatch((1, ts1, uuid1, 1.5f, BigDecimal("9.99"), "a".getBytes("UTF-8")))
        result <- batch.executeBatch()
      yield result
    }
  yield batchResult
}
```

#### Option support for nullable columns

Wrap any supported type in `Option` to represent nullable columns — `Some(value)` binds the value and `None` inserts SQL NULL:

```scala sc-compile-with:Imp.scala
val result = DuckDBConnection.withConnection() { conn =>
  for
    _ <- conn.executeUpdate("CREATE TABLE contacts (id INTEGER, email VARCHAR)")
    batchResult <- conn.withBatch("INSERT INTO contacts VALUES (?, ?)") { batch =>
      for
        _ <- batch.addBatch((1, Option("alice@example.com")))
        _ <- batch.addBatch((2, Option.empty[String]))
        r <- batch.executeBatch()
      yield r
    }
  yield batchResult
}
```

#### Custom ParameterBinder

For types not covered by the built-in binders, implement the `ParameterBinder` type class:

```scala sc-compile-with:Imp.scala
import com.softinio.duck4s.algebra.{ParameterBinder, DuckDBPreparedStatement, DuckDBError}

case class UserId(value: Long)

given ParameterBinder[UserId] with
  def bind(stmt: DuckDBPreparedStatement, index: Int, value: UserId): Either[DuckDBError, Unit] =
    stmt.setLong(index, value.value).map(_ => ())
```

### Transactions

Use transactions for atomic operations:

```scala sc-compile-with:Imp.scala
val result = DuckDBConnection.withConnection() { conn =>
  for
    _ <- conn.executeUpdate("CREATE TABLE accounts (id INTEGER, balance DOUBLE)")
    _ <- conn.executeUpdate("INSERT INTO accounts VALUES (1, 1000.0), (2, 500.0)")

    transferResult <- conn.withTransaction { txConn =>
      for
        _ <- txConn.executeUpdate("UPDATE accounts SET balance = balance - 100 WHERE id = 1")
        _ <- txConn.executeUpdate("UPDATE accounts SET balance = balance + 100 WHERE id = 2")
      yield "Transfer completed"
    }
  yield transferResult
}
```

### Error Handling

Duck4s uses `Either[DuckDBError, T]` for functional error handling:

```scala sc-compile-with:Imp.scala
val result = DuckDBConnection.withConnection() { conn =>
  conn.executeQuery("SELECT * FROM nonexistent_table")
}

result match
  case Right(resultSet) =>
    // Handle successful result
    resultSet.close()
  case Left(DuckDBError.QueryError(message, sql, cause)) =>
    println(s"Query failed: $message")
    println(s"SQL: $sql")
    cause.foreach(t => println(s"Cause: ${t.getMessage}"))
  case Left(error) =>
    println(s"Other error: $error")
```

## Configuration Options

Customize your DuckDB connection:

```scala sc-compile-with:Imp.scala
val config = DuckDBConfig(
  mode = ConnectionMode.Persistent("/path/to/database.db"),
  readOnly = false,
  tempDirectory = Some("/tmp/duckdb"),
  streamResults = true,
  additionalProperties = Map(
    "memory_limit" -> "1GB",
    "threads" -> "4"
  )
)

DuckDBConnection.withConnection(config) { conn =>
  // Use configured connection
  Right("Success")
}
```

## Cats-Effect Integration

If you prefer a purely functional style with cats-effect and fs2, add the `duck4s-cats-effect` module and import `com.softinio.duck4s.effect.*`. Connections are managed as `Resource[IO, DuckDBConnection]`, queries return `IO`, and result sets can be consumed as `fs2.Stream`.

```scala sc:nocompile
import cats.effect.{IO, IOApp}
import com.softinio.duck4s.effect.*

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    DuckDBIO.connect().use { conn =>
      for
        _ <- conn.executeUpdateIO("CREATE TABLE t (id INTEGER, name VARCHAR)")
        _ <- conn.executeUpdateIO("INSERT INTO t VALUES (1, 'Alice'), (2, 'Bob')")
        rows <- DuckDBIO.stream(conn, "SELECT * FROM t") { rs =>
          rs.getString("name")
        }.compile.toList
        _ <- IO(println(rows))
      yield ()
    }
```

See the [Cats-Effect Integration](cats-effect.html) guide for the full API reference.

## Next Steps

- Explore the [API Documentation](../index.html) for complete reference
- Read the [Cats-Effect Integration](cats-effect.html) guide for effectful usage
- Learn about advanced batch operations and type classes
- Check out the DuckDB [official documentation](https://duckdb.org/docs/) for SQL features
- Browse the source code on [GitHub](https://github.com/softinio/duck4s)

## Support

- [GitHub Issues](https://github.com/softinio/duck4s/issues) - Bug reports and feature requests
- [Discussions](https://github.com/softinio/duck4s/discussions) - Questions and community support
