---
title: Getting Started
---

# Getting Started with Duck4s

This guide will help you get started with duck4s, a Scala 3 wrapper for DuckDB that provides type-safe, functional access to analytical database operations.

## Installation

### SBT

Add duck4s to your `build.sbt`:

```sbt
libraryDependencies += "com.softinio" %% "duck4s" % "0.1.0"
```

### Mill

Add duck4s to your `build.sc`:

```scala sc:nocompile
def ivyDeps = Agg(
  ivy"com.softinio::duck4s::0.1.0"
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

## Next Steps

- Explore the [API Documentation](../index.html) for complete reference
- Learn about advanced batch operations and type classes
- Check out the DuckDB [official documentation](https://duckdb.org/docs/) for SQL features
- Browse the source code on [GitHub](https://github.com/softinio/duck4s)

## Support

- [GitHub Issues](https://github.com/softinio/duck4s/issues) - Bug reports and feature requests
- [Discussions](https://github.com/softinio/duck4s/discussions) - Questions and community support
