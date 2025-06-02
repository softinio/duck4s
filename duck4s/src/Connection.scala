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

package com.softinio.duck4s

import com.softinio.duck4s.algebra.{
  ConnectionMode,
  DuckDBBatch,
  DuckDBBatchResult,
  DuckDBConfig,
  DuckDBError,
  DuckDBPreparedStatement,
  DuckDBResultSet
}
import java.sql.{Connection as JDBCConnection, DriverManager, SQLException}
import java.util.Properties
import scala.util.Using
import scala.util.control.NonFatal

/** A Scala 3 wrapper for DuckDB JDBC connections providing type-safe,
  * functional access to DuckDB.
  *
  * This class wraps the underlying JDBC connection and provides idiomatic Scala
  * methods for executing queries, managing transactions, and working with
  * prepared statements.
  *
  * @constructor
  *   Creates a new DuckDB connection wrapper
  * @param underlying
  *   The underlying JDBC connection to DuckDB
  *
  * @example
  *   {{{ import com.softinio.duck4s.* import com.softinio.duck4s.algebra.*
  *
  * // Create an in-memory connection val result =
  * DuckDBConnection.withConnection() { conn => for _ <-
  * conn.executeUpdate("CREATE TABLE users (id INTEGER, name VARCHAR)") _ <-
  * conn.executeUpdate("INSERT INTO users VALUES (1, 'Alice')") rs <-
  * conn.executeQuery("SELECT * FROM users") yield while rs.next() do
  * println(s"${rs.getInt("id")}: ${rs.getString("name")}") rs.close() } }}}
  *
  * @see
  *   [[com.softinio.duck4s.algebra.DuckDBConfig]] for connection configuration
  *   options
  * @see
  *   [[com.softinio.duck4s.algebra.DuckDBError]] for error handling
  * @since 0.1.0
  */
class DuckDBConnection private (
    private[duck4s] val underlying: JDBCConnection
) extends AutoCloseable:
  export underlying.{close, isClosed, isValid}

  /** Creates a duplicate of this DuckDB connection.
    *
    * This method uses DuckDB's native connection duplication functionality to
    * create a new connection that shares the same database instance but
    * operates independently.
    *
    * @return
    *   Either a [[algebra.DuckDBError.InvalidStateError]] if the connection is
    *   not a DuckDB connection, a [[algebra.DuckDBError.ConnectionError]] if
    *   duplication fails, or a new [[DuckDBConnection]]
    * @since 0.1.0
    */
  def duplicate(): Either[DuckDBError, DuckDBConnection] =
    try
      val duckConn = underlying.asInstanceOf[org.duckdb.DuckDBConnection]
      Right(new DuckDBConnection(duckConn.duplicate()))
    catch
      case e: ClassCastException =>
        Left(
          DuckDBError.InvalidStateError(
            "Connection is not a DuckDB connection"
          )
        )
      case NonFatal(e) =>
        Left(
          DuckDBError.ConnectionError(
            s"Failed to duplicate connection: ${e.getMessage}",
            Some(e)
          )
        )

  /** Executes a block of operations within a database transaction.
    *
    * This method temporarily disables autocommit, executes the provided block,
    * and then either commits the transaction on success or rolls it back on
    * failure. Autocommit is restored regardless of the outcome.
    *
    * @param block
    *   A function that receives this connection and returns an Either with the
    *   result
    * @tparam T
    *   The type of the successful result
    * @return
    *   Either a [[algebra.DuckDBError.TransactionError]] if the transaction
    *   fails, or the result of the block
    *
    * @example
    *   {{{ conn.withTransaction { txConn => for _ <-
    *   txConn.executeUpdate("INSERT INTO users VALUES (1, 'Alice')") _ <-
    *   txConn.executeUpdate("INSERT INTO users VALUES (2, 'Bob')") yield "Two
    *   users inserted" } }}}
    * @since 0.1.0
    */
  def withTransaction[T](
      block: DuckDBConnection => Either[DuckDBError, T]
  ): Either[DuckDBError, T] =
    try
      underlying.setAutoCommit(false)
      block(this) match
        case Right(result) =>
          underlying.commit()
          Right(result)
        case Left(error) =>
          underlying.rollback()
          Left(error)
    catch
      case NonFatal(e) =>
        try underlying.rollback()
        catch case NonFatal(_) => () // Ignore rollback errors
        Left(
          DuckDBError.TransactionError(
            s"Transaction failed: ${e.getMessage}",
            Some(e)
          )
        )
    finally
      try underlying.setAutoCommit(true)
      catch case NonFatal(_) => () // Ignore autocommit reset errors

  /** Creates a prepared statement for the given SQL query.
    *
    * This method compiles the SQL statement and returns a prepared statement
    * that can be executed multiple times with different parameters efficiently.
    *
    * @param sql
    *   The SQL statement to prepare, may contain parameter placeholders (?)
    * @return
    *   Either a [[algebra.DuckDBError.QueryError]] if preparation fails, or a
    *   [[algebra.DuckDBPreparedStatement]]
    *
    * @example
    *   {{{\n * for stmt <- conn.prepareStatement("SELECT * FROM users WHERE id =
    *   ?") _ <- stmt.setInt(1, 42) rs <- stmt.executeQuery() yield rs }}}\n *
    * @see
    *   [[algebra.DuckDBPreparedStatement]] for parameter binding and execution
    * @since 0.1.0
    */
  def prepareStatement(
      sql: String
  ): Either[DuckDBError, DuckDBPreparedStatement] =
    try
      val stmt = underlying.prepareStatement(sql)
      Right(DuckDBPreparedStatement(stmt, sql))
    catch
      case e: SQLException =>
        Left(
          DuckDBError.QueryError(
            s"Failed to prepare statement: ${e.getMessage}",
            sql,
            Some(e)
          )
        )
      case NonFatal(e) =>
        Left(
          DuckDBError.QueryError(
            s"Unexpected error preparing statement: ${e.getMessage}",
            sql,
            Some(e)
          )
        )

  /** Creates a batch for executing the given SQL statement multiple times.
    *
    * This method creates a prepared statement optimized for batch execution,
    * allowing efficient insertion or update of multiple rows.
    *
    * @param sql
    *   The SQL statement to prepare for batch execution
    * @return
    *   Either a [[algebra.DuckDBError.QueryError]] if preparation fails, or a
    *   [[algebra.DuckDBBatch]]
    *
    * @example
    *   {{{\n * for batch <- conn.prepareBatch("INSERT INTO users (id, name)
    *   VALUES (?, ?)") _ <- batch.addBatch(1, "Alice") _ <- batch.addBatch(2,
    *   "Bob") result <- batch.executeBatch() yield result }}}\n *
    * @see
    *   [[algebra.DuckDBBatch]] for batch parameter binding and execution
    * @since 0.1.0
    */
  def prepareBatch(sql: String): Either[DuckDBError, DuckDBBatch] =
    prepareStatement(sql).map(DuckDBBatch(_))

object DuckDBConnection:
  private def connectionString(config: DuckDBConfig): String = config.mode match
    case ConnectionMode.InMemory         => "jdbc:duckdb:"
    case ConnectionMode.Persistent(path) => s"jdbc:duckdb:$path"

  private def properties(config: DuckDBConfig): Properties =
    val props = Properties()

    if config.readOnly then props.setProperty("duckdb.read_only", "true")

    config.tempDirectory.foreach: dir =>
      props.setProperty("temp_directory", dir)

    if config.streamResults then
      props.setProperty("jdbc_stream_results", "true")

    config.additionalProperties.foreach: (key, value) =>
      props.setProperty(key, value)

    props

  /** Creates a new DuckDB connection with the specified configuration.
    *
    * This method establishes a connection to DuckDB using the provided
    * configuration. The connection can be either in-memory or persistent to a
    * file.
    *
    * @param config
    *   The database configuration including connection mode, read-only flag,
    *   temporary directory, and additional properties. Defaults to in-memory
    *   mode.
    * @return
    *   Either a [[algebra.DuckDBError.ConnectionError]] if connection fails, or
    *   a [[DuckDBConnection]]
    *
    * @example
    *   {{{\n * // Create an in-memory connection val memConn =
    *   DuckDBConnection.connect()
    *
    * // Create a persistent connection val persistentConn =
    * DuckDBConnection.connect( DuckDBConfig.persistent("/path/to/database.db")
    * ) }}}\n *
    * @see
    *   [[algebra.DuckDBConfig]] for configuration options
    * @since 0.1.0
    */
  def connect(
      config: DuckDBConfig = DuckDBConfig.inMemory
  ): Either[DuckDBError, DuckDBConnection] =
    try
      val conn = DriverManager.getConnection(
        connectionString(config),
        properties(config)
      )
      Right(new DuckDBConnection(conn))
    catch
      case e: SQLException =>
        Left(
          DuckDBError.ConnectionError(
            s"Failed to connect to DuckDB: ${e.getMessage}",
            Some(e)
          )
        )
      case NonFatal(e) =>
        Left(
          DuckDBError.ConnectionError(
            s"Unexpected error connecting to DuckDB: ${e.getMessage}",
            Some(e)
          )
        )

  /** Executes a block of operations with a DuckDB connection that is
    * automatically closed.
    *
    * This method creates a new connection, executes the provided block, and
    * ensures the connection is properly closed regardless of the outcome. This
    * is the recommended way to work with DuckDB connections.
    *
    * @param config
    *   The database configuration. Defaults to in-memory mode.
    * @param block
    *   A function that receives the connection and returns an Either with the
    *   result
    * @tparam T
    *   The type of the successful result
    * @return
    *   Either a [[algebra.DuckDBError.ConnectionError]] if connection or
    *   execution fails, or the result of the block
    *
    * @example
    *   {{{\n * val result = DuckDBConnection.withConnection() { conn => for _
    *   <- conn.executeUpdate("CREATE TABLE test (id INTEGER)") count <-
    *   conn.executeUpdate("INSERT INTO test VALUES (1)") yield count } }}}\n *
    * @see
    *   [[connect]] for connection creation without automatic resource
    *   management
    * @since 0.1.0
    */
  def withConnection[T](config: DuckDBConfig = DuckDBConfig.inMemory)(
      block: DuckDBConnection => Either[DuckDBError, T]
  ): Either[DuckDBError, T] =
    connect(config).flatMap: conn =>
      try
        val result = block(conn)
        conn.close()
        result
      catch
        case NonFatal(e) =>
          try conn.close()
          catch case NonFatal(_) => () // Ignore close errors
          Left(
            DuckDBError.ConnectionError(
              s"Error during connection usage: ${e.getMessage}",
              Some(e)
            )
          )

extension (connection: DuckDBConnection)
  /** Executes a SQL query and returns the result set.
    *
    * This method executes a SELECT query and returns a result set that can be
    * used to iterate over the returned rows. The result set should be closed
    * when no longer needed.
    *
    * @param sql
    *   The SQL SELECT query to execute
    * @return
    *   Either a [[algebra.DuckDBError.QueryError]] if execution fails, or a
    *   [[algebra.DuckDBResultSet]]
    *
    * @example
    *   {{{\n * for rs <- conn.executeQuery("SELECT * FROM users WHERE age >
    *   18") yield while rs.next() do println(s"User: ${rs.getString("name")}")
    *   rs.close() }}}\n *
    * @see
    *   [[algebra.DuckDBResultSet]] for result set navigation and data
    *   extraction
    * @since 0.1.0
    */
  def executeQuery(sql: String): Either[DuckDBError, DuckDBResultSet] =
    try
      val stmt = connection.underlying.createStatement()
      val rs = stmt.executeQuery(sql)
      Right(DuckDBResultSet(rs, stmt))
    catch
      case e: SQLException =>
        Left(
          DuckDBError.QueryError(
            s"Failed to execute query: ${e.getMessage}",
            sql,
            Some(e)
          )
        )
      case NonFatal(e) =>
        Left(
          DuckDBError.QueryError(
            s"Unexpected error executing query: ${e.getMessage}",
            sql,
            Some(e)
          )
        )

  /** Executes a SQL update statement (INSERT, UPDATE, DELETE, DDL).
    *
    * This method executes SQL statements that modify the database or its
    * structure. It returns the number of affected rows for DML statements.
    *
    * @param sql
    *   The SQL statement to execute (INSERT, UPDATE, DELETE, CREATE TABLE,
    *   etc.)
    * @return
    *   Either a [[algebra.DuckDBError.QueryError]] if execution fails, or the
    *   number of affected rows
    *
    * @example
    *   {{{\n * for count <- conn.executeUpdate("INSERT INTO users (name, age)
    *   VALUES ('Alice', 25)") yield println(s"$count rows inserted") }}}\n *
    * @since 0.1.0
    */
  def executeUpdate(sql: String): Either[DuckDBError, Int] =
    try
      Using.resource(connection.underlying.createStatement()): stmt =>
        Right(stmt.executeUpdate(sql))
    catch
      case e: SQLException =>
        Left(
          DuckDBError.QueryError(
            s"Failed to execute update: ${e.getMessage}",
            sql,
            Some(e)
          )
        )
      case NonFatal(e) =>
        Left(
          DuckDBError.QueryError(
            s"Unexpected error executing update: ${e.getMessage}",
            sql,
            Some(e)
          )
        )

  /** Executes a block of operations with a prepared statement that is
    * automatically closed.
    *
    * This method creates a prepared statement, executes the provided block, and
    * ensures the statement is properly closed regardless of the outcome.
    *
    * @param sql
    *   The SQL statement to prepare
    * @param block
    *   A function that receives the prepared statement and returns an Either
    *   with the result
    * @tparam T
    *   The type of the successful result
    * @return
    *   Either a [[algebra.DuckDBError.QueryError]] if preparation or execution
    *   fails, or the result of the block
    *
    * @example
    *   {{{\n * conn.withPreparedStatement("SELECT * FROM users WHERE id = ?") {
    *   stmt => for _ <- stmt.setInt(1, 42) rs <- stmt.executeQuery() yield rs }
    *   }}}\n *
    * @see
    *   [[DuckDBConnection.prepareStatement]] for manual prepared statement
    *   management
    * @see
    *   [[algebra.DuckDBPreparedStatement]] for parameter binding and execution
    * @since 0.1.0
    */
  def withPreparedStatement[T](sql: String)(
      block: DuckDBPreparedStatement => Either[DuckDBError, T]
  ): Either[DuckDBError, T] =
    connection
      .prepareStatement(sql)
      .flatMap: stmt =>
        try
          val result = block(stmt)
          stmt.close()
          result
        catch
          case NonFatal(e) =>
            try stmt.close()
            catch case NonFatal(_) => () // Ignore close errors
            Left(
              DuckDBError.QueryError(
                s"Error during prepared statement usage: ${e.getMessage}",
                sql,
                Some(e)
              )
            )

  /** Executes a block of operations with a batch that is automatically closed.
    *
    * This method creates a batch for the given SQL statement, executes the
    * provided block, and ensures the batch is properly closed regardless of the
    * outcome.
    *
    * @param sql
    *   The SQL statement to prepare for batch execution
    * @param block
    *   A function that receives the batch and returns an Either with the result
    * @tparam T
    *   The type of the successful result
    * @return
    *   Either a [[algebra.DuckDBError.QueryError]] if preparation or execution
    *   fails, or the result of the block
    *
    * @example
    *   {{{\n * conn.withBatch("INSERT INTO users (id, name) VALUES (?, ?)") {
    *   batch => for _ <- batch.addBatch(1, "Alice") _ <- batch.addBatch(2,
    *   "Bob") result <- batch.executeBatch() yield result } }}}\n *
    * @see
    *   [[DuckDBConnection.prepareBatch]] for manual batch management
    * @see
    *   [[algebra.DuckDBBatch]] for batch parameter binding and execution
    * @since 0.1.0
    */
  def withBatch[T](sql: String)(
      block: DuckDBBatch => Either[DuckDBError, T]
  ): Either[DuckDBError, T] =
    connection
      .prepareBatch(sql)
      .flatMap: batch =>
        try
          val result = block(batch)
          batch.close()
          result
        catch
          case NonFatal(e) =>
            try batch.close()
            catch case NonFatal(_) => () // Ignore close errors
            Left(
              DuckDBError.QueryError(
                s"Error during batch usage: ${e.getMessage}",
                sql,
                Some(e)
              )
            )
