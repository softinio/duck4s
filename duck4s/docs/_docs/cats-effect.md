---
title: Cats-Effect Integration
---

# Cats-Effect Integration

The `duck4s-cats-effect` module provides a purely functional interface for DuckDB built on [cats-effect](https://typelevel.org/cats-effect/) and [fs2](https://fs2.io/). Connections are managed as `Resource[IO, DuckDBConnection]`, all JDBC calls are wrapped in `IO.blocking`, and errors are raised as `DuckDBException` rather than returned as `Either` values.

## Installation

### SBT

```sbt
libraryDependencies += "com.softinio" %% "duck4s-cats-effect" % "0.1.0"
```

### Mill

```scala sc:nocompile
def ivyDeps = Agg(
  ivy"com.softinio::duck4s-cats-effect::0.1.0"
)
```

## Imports

```scala sc:nocompile
import cats.effect.{IO, Resource, IOApp}
import fs2.Stream
import com.softinio.duck4s.algebra.*
import com.softinio.duck4s.effect.*
```

## Connection Management

Use `DuckDBIO.connect()` to acquire a connection as a `Resource`. The connection is closed automatically when the resource scope exits.

```scala sc:nocompile
// In-memory database (default)
val program: IO[Unit] =
  DuckDBIO.connect().use { conn =>
    conn.executeUpdateIO("CREATE TABLE t (id INTEGER)").void
  }

// Persistent database
val config = DuckDBConfig.persistent("/path/to/database.db")
val persistentProgram: IO[Unit] =
  DuckDBIO.connect(config).use { conn =>
    conn.executeUpdateIO("CREATE TABLE t (id INTEGER)").void
  }
```

> **Thread safety**: DuckDB connections are **not thread-safe**. Do not share a single `DuckDBConnection` across fibers. Each fiber should use its own connection, either via a separate `DuckDBIO.connect()` scope or by calling `conn.duplicate()`.

## Executing Queries

### DDL and Updates

`executeUpdateIO` runs INSERT, UPDATE, DELETE, and DDL statements, returning the affected row count:

```scala sc:nocompile
DuckDBIO.connect().use { conn =>
  for
    _ <- conn.executeUpdateIO("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)")
    n <- conn.executeUpdateIO("INSERT INTO users VALUES (1, 'Alice', 25), (2, 'Bob', 30)")
    _ <- IO(println(s"Inserted $n rows"))
  yield ()
}
```

### SELECT Queries

`executeQueryIO` returns a `DuckDBResultSet` wrapped in `IO`. You are responsible for closing the result set:

```scala sc:nocompile
DuckDBIO.connect().use { conn =>
  for
    _ <- conn.executeUpdateIO("CREATE TABLE users (id INTEGER, name VARCHAR)")
    _ <- conn.executeUpdateIO("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')")
    rs <- conn.executeQueryIO("SELECT * FROM users ORDER BY id")
    _ <- IO {
      while rs.next() do
        println(s"${rs.getInt("id")}: ${rs.getString("name")}")
      rs.close()
    }
  yield ()
}
```

## Streaming Results with fs2

`DuckDBIO.stream` streams rows from a SELECT query as an `fs2.Stream[IO, A]`. The result set is opened and closed within the stream's resource scope — no manual cleanup needed.

```scala sc:nocompile
DuckDBIO.connect().use { conn =>
  for
    _ <- conn.executeUpdateIO("CREATE TABLE products (id INTEGER, name VARCHAR, price DOUBLE)")
    _ <- conn.executeUpdateIO("""
      INSERT INTO products VALUES
        (1, 'Widget', 9.99),
        (2, 'Gadget', 24.99),
        (3, 'Doohickey', 4.99)
    """)
    names <- DuckDBIO
      .stream(conn, "SELECT name, price FROM products WHERE price > 5.0") { rs =>
        s"${rs.getString("name")} ($$${rs.getDouble("price")})"
      }
      .compile.toList
    _ <- IO(names.foreach(println))
  yield ()
}
```

The row mapper function receives the `DuckDBResultSet` positioned at each row and should extract the values synchronously.

## Prepared Statements

### One-shot with automatic cleanup

`withPreparedStatementIO` prepares a statement, runs a block, then closes it automatically:

```scala sc:nocompile
DuckDBIO.connect().use { conn =>
  for
    _ <- conn.executeUpdateIO("CREATE TABLE items (id INTEGER, label VARCHAR)")
    count <- conn.withPreparedStatementIO("INSERT INTO items VALUES (?, ?)") { stmt =>
      for
        _ <- IO.blocking(stmt.setInt(1, 42))
        _ <- IO.blocking(stmt.setString(2, "example"))
        n <- IO.blocking(stmt.executeUpdate())
      yield n
    }
    _ <- IO(println(s"Inserted $count row(s)"))
  yield ()
}
```

### Manual lifecycle

`prepareStatementIO` returns an `IO[DuckDBPreparedStatement]` when you need to manage the lifecycle yourself:

```scala sc:nocompile
import cats.effect.Resource

DuckDBIO.connect().use { conn =>
  Resource
    .make(conn.prepareStatementIO("SELECT * FROM items WHERE id = ?"))(stmt =>
      IO.blocking(stmt.close())
    )
    .use { stmt =>
      IO.blocking(stmt.setInt(1, 42)) >>
        IO.blocking(stmt.executeQuery()).flatMap { rs =>
          IO {
            while rs.next() do println(rs.getString("label"))
            rs.close()
          }
        }
    }
}
```

## Batch Operations

### One-shot with automatic cleanup

`withBatchIO` prepares a batch, runs a block, then closes it automatically:

```scala sc:nocompile
DuckDBIO.connect().use { conn =>
  for
    _ <- conn.executeUpdateIO("CREATE TABLE employees (id INTEGER, name VARCHAR, salary DOUBLE)")
    result <- conn.withBatchIO("INSERT INTO employees VALUES (?, ?, ?)") { batch =>
      for
        _ <- IO.blocking(batch.addBatch((1, "Alice", 75000.0), (2, "Bob", 80000.0), (3, "Charlie", 85000.0)))
        r <- IO.blocking(batch.executeBatch())
      yield r
    }
    _ <- IO(println(s"Inserted ${result.successCount} row(s), ${result.failureCount} failure(s)"))
  yield ()
}
```

### Manual lifecycle

`prepareBatchIO` returns an `IO[DuckDBBatch]` for manual control:

```scala sc:nocompile
import cats.effect.Resource

DuckDBIO.connect().use { conn =>
  Resource
    .make(conn.prepareBatchIO("INSERT INTO employees VALUES (?, ?, ?)"))(b =>
      IO.blocking(b.close())
    )
    .use { batch =>
      IO.blocking(batch.addBatch((4, "Dana", 90000.0))) >>
        IO.blocking(batch.executeBatch()).void
    }
}
```

## Transactions

`withTransactionIO` disables auto-commit, runs a block, and commits on success or rolls back on any raised error. Auto-commit is restored in a `guarantee` finalizer regardless of outcome.

```scala sc:nocompile
DuckDBIO.connect().use { conn =>
  for
    _ <- conn.executeUpdateIO("CREATE TABLE accounts (id INTEGER, balance DOUBLE)")
    _ <- conn.executeUpdateIO("INSERT INTO accounts VALUES (1, 1000.0), (2, 500.0)")
    _ <- conn.withTransactionIO { txConn =>
      for
        _ <- txConn.executeUpdateIO("UPDATE accounts SET balance = balance - 200 WHERE id = 1")
        _ <- txConn.executeUpdateIO("UPDATE accounts SET balance = balance + 200 WHERE id = 2")
      yield ()
    }
  yield ()
}
```

If either update raises an error, the whole transaction is rolled back automatically.

## Error Handling

The cats-effect module raises errors as `DuckDBException` (a `RuntimeException` subclass) rather than returning `Either`. Use standard cats-effect error handling:

```scala sc:nocompile
import com.softinio.duck4s.effect.DuckDBException

DuckDBIO.connect().use { conn =>
  conn
    .executeQueryIO("SELECT * FROM nonexistent_table")
    .handleErrorWith {
      case ex: DuckDBException =>
        IO(println(s"DuckDB error: ${ex.getMessage}")) >> IO(println(s"Underlying: ${ex.error}"))
      case ex =>
        IO(println(s"Unexpected error: ${ex.getMessage}"))
    }
    .void
}
```

You can also use `attempt` to recover an `Either[Throwable, A]`:

```scala sc:nocompile
DuckDBIO.connect().use { conn =>
  conn.executeQueryIO("SELECT * FROM nonexistent_table").attempt.flatMap {
    case Right(rs) => IO(rs.close())
    case Left(ex)  => IO(println(s"Failed: ${ex.getMessage}"))
  }
}
```

## Complete Example

```scala sc:nocompile
import cats.effect.{IO, IOApp}
import com.softinio.duck4s.algebra.*
import com.softinio.duck4s.effect.*

object Main extends IOApp.Simple:
  def run: IO[Unit] =
    DuckDBIO.connect().use { conn =>
      for
        _ <- conn.executeUpdateIO("""
          CREATE TABLE orders (
            id      INTEGER,
            product VARCHAR,
            qty     INTEGER,
            price   DOUBLE
          )
        """)

        _ <- conn.withBatchIO("INSERT INTO orders VALUES (?, ?, ?, ?)") { batch =>
          for
            _ <- IO.blocking(batch.addBatch(
              (1, "Widget",    10, 9.99),
              (2, "Gadget",     5, 24.99),
              (3, "Doohickey", 20, 4.99)
            ))
            r <- IO.blocking(batch.executeBatch())
            _ <- IO(println(s"Inserted ${r.successCount} order(s)"))
          yield ()
        }

        total <- DuckDBIO
          .stream(conn, "SELECT product, qty * price AS subtotal FROM orders") { rs =>
            rs.getString("product") -> rs.getDouble("subtotal")
          }
          .evalTap { case (product, subtotal) => IO(println(f"$product%-12s $$${subtotal}%.2f")) }
          .map(_._2)
          .compile.fold(0.0)(_ + _)

        _ <- IO(println(f"Total: $$${total}%.2f"))
      yield ()
    }
```

## Next Steps

- [Getting Started](getting-started.html) - Core synchronous API
- [API Documentation](../index.html) - Complete API reference
- [cats-effect documentation](https://typelevel.org/cats-effect/)
- [fs2 documentation](https://fs2.io/)
