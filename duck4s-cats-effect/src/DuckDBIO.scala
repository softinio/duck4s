/*
 * Copyright 2025 Salar Rahmanian
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.softinio.duck4s.effect

import cats.effect.{IO, Resource}
import fs2.Stream
import com.softinio.duck4s.*
import com.softinio.duck4s.algebra.*

/** Cats-effect integration for duck4s, providing [[cats.effect.Resource]]-based
  * connection management and [[fs2.Stream]]-based result set streaming.
  *
  * All JDBC calls are wrapped in [[cats.effect.IO.blocking]] to avoid blocking
  * the cats-effect thread pool. Errors are raised as [[DuckDBException]] rather
  * than returned as `Either` values.
  *
  * @note
  *   DuckDB connections are NOT thread-safe. Do not share a single connection
  *   across fibers. Use `duplicate()` or open separate connections per fiber.
  *
  * @example
  *   {{{ import com.softinio.duck4s.effect.*
  *
  * val program: IO[Unit] = DuckDBIO.connect().use { conn => for _ <-
  * conn.executeUpdateIO("CREATE TABLE t (id INTEGER)") _ <-
  * conn.executeUpdateIO("INSERT INTO t VALUES (1)") rows <-
  * DuckDBIO.stream(conn, "SELECT id FROM t")(_.getInt("id")).compile.toList _
  * <- IO(println(rows)) yield () } }}}
  */
object DuckDBIO:

  /** Lifts an `Either[DuckDBError, T]` into `IO[T]`, raising errors as
    * [[DuckDBException]].
    */
  private[effect] def liftE[T](e: Either[DuckDBError, T]): IO[T] =
    IO.fromEither(e.left.map(DuckDBException.from))

  /** Acquires a DuckDB connection as a cats-effect [[Resource]]. The connection
    * is closed automatically on release.
    *
    * @param config
    *   Database configuration. Defaults to in-memory mode.
    * @return
    *   A [[Resource]] that manages the connection lifecycle.
    */
  def connect(
      config: DuckDBConfig = DuckDBConfig.inMemory
  ): Resource[IO, DuckDBConnection] =
    Resource.make(
      IO.blocking(DuckDBConnection.connect(config)).flatMap(liftE)
    )(conn => IO.blocking(conn.close()))

  /** Streams rows from a SQL SELECT query as a [[fs2.Stream]].
    *
    * The result set is opened and closed within the stream's resource scope.
    * Each row is mapped using the provided function while the cursor is
    * positioned at that row.
    *
    * @param conn
    *   The DuckDB connection to use for the query.
    * @param sql
    *   The SQL SELECT statement to execute.
    * @param f
    *   Row mapper called for each row with the result set positioned at that
    *   row.
    * @tparam A
    *   The type of each emitted element.
    * @return
    *   A [[fs2.Stream]] that emits one element per row.
    */
  def stream[A](conn: DuckDBConnection, sql: String)(
      f: DuckDBResultSet => A
  ): Stream[IO, A] =
    Stream
      .resource(
        Resource.make(
          IO.blocking(conn.executeQuery(sql)).flatMap(liftE)
        )(rs => IO.blocking(rs.close()))
      )
      .flatMap: rs =>
        Stream.unfoldEval(rs): rs =>
          IO.blocking(rs.next())
            .map(hasNext => Option.when(hasNext)(f(rs) -> rs))

/** Extension methods providing effectful versions of [[DuckDBConnection]]
  * operations. Import `com.softinio.duck4s.effect.*` to use these.
  */
extension (conn: DuckDBConnection)

  /** Executes a SQL SELECT query and returns the result set wrapped in IO.
    *
    * @param sql
    *   The SQL SELECT statement to execute.
    * @return
    *   An `IO[DuckDBResultSet]`. The caller is responsible for closing the
    *   result set.
    */
  def executeQueryIO(sql: String): IO[DuckDBResultSet] =
    IO.blocking(conn.executeQuery(sql)).flatMap(DuckDBIO.liftE)

  /** Executes a SQL update statement (INSERT, UPDATE, DELETE, DDL) and returns
    * the affected row count wrapped in IO.
    *
    * @param sql
    *   The SQL statement to execute.
    * @return
    *   An `IO[Int]` with the number of affected rows.
    */
  def executeUpdateIO(sql: String): IO[Int] =
    IO.blocking(conn.executeUpdate(sql)).flatMap(DuckDBIO.liftE)

  /** Creates a prepared statement wrapped in IO.
    *
    * @param sql
    *   The SQL statement to prepare.
    * @return
    *   An `IO[DuckDBPreparedStatement]`. The caller is responsible for closing
    *   the statement.
    */
  def prepareStatementIO(sql: String): IO[DuckDBPreparedStatement] =
    IO.blocking(conn.prepareStatement(sql)).flatMap(DuckDBIO.liftE)

  /** Executes a block with a prepared statement that is automatically closed.
    *
    * @param sql
    *   The SQL statement to prepare.
    * @param block
    *   A function receiving the prepared statement and returning an `IO[T]`.
    * @tparam T
    *   The result type.
    * @return
    *   An `IO[T]` with the result of the block.
    */
  def withPreparedStatementIO[T](
      sql: String
  )(block: DuckDBPreparedStatement => IO[T]): IO[T] =
    Resource
      .make(conn.prepareStatementIO(sql))(stmt => IO.blocking(stmt.close()))
      .use(block)

  /** Creates a batch wrapped in IO.
    *
    * @param sql
    *   The SQL statement to prepare for batch execution.
    * @return
    *   An `IO[DuckDBBatch]`. The caller is responsible for closing the batch.
    */
  def prepareBatchIO(sql: String): IO[DuckDBBatch] =
    IO.blocking(conn.prepareBatch(sql)).flatMap(DuckDBIO.liftE)

  /** Executes a block with a batch that is automatically closed.
    *
    * @param sql
    *   The SQL statement to prepare for batch execution.
    * @param block
    *   A function receiving the batch and returning an `IO[T]`.
    * @tparam T
    *   The result type.
    * @return
    *   An `IO[T]` with the result of the block.
    */
  def withBatchIO[T](sql: String)(block: DuckDBBatch => IO[T]): IO[T] =
    Resource
      .make(conn.prepareBatchIO(sql))(batch => IO.blocking(batch.close()))
      .use(block)

  /** Executes a block within a database transaction.
    *
    * Disables auto-commit before running the block. On success, commits the
    * transaction. On failure (any raised error), rolls back the transaction.
    * Auto-commit is restored to `true` in a `guarantee` finalizer regardless of
    * outcome.
    *
    * @param block
    *   A function receiving this connection and returning an `IO[T]`.
    * @tparam T
    *   The result type.
    * @return
    *   An `IO[T]` that commits on success or rolls back on error.
    */
  def withTransactionIO[T](block: DuckDBConnection => IO[T]): IO[T] =
    IO.blocking(conn.setAutoCommit(false)) >>
      block(conn).attempt
        .flatMap {
          case Right(t) => IO.blocking(conn.commit()).as(t)
          case Left(e)  => IO.blocking(conn.rollback()) >> IO.raiseError(e)
        }
        .guarantee(IO.blocking(conn.setAutoCommit(true)).void)
