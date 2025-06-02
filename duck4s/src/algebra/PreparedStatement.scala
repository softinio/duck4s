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

package com.softinio.duck4s.algebra

import java.sql.{
  PreparedStatement as JDBCPreparedStatement,
  ResultSet,
  SQLException
}
import scala.util.Using
import scala.util.control.NonFatal

/** A wrapper around a JDBC PreparedStatement that provides type-safe parameter
  * binding and error handling with DuckDB-specific error types.
  *
  * This class encapsulates a JDBC PreparedStatement and provides methods for
  * setting parameters and executing queries/updates in a functional style. All
  * parameter setting methods return Either types for error handling, making it
  * safe to compose operations.
  *
  * @param underlying
  *   the underlying JDBC PreparedStatement
  * @param sql
  *   the SQL query string, used for error reporting
  *
  * @example
  *   {{{ // Setting parameters and executing a query val result = for { stmt <-
  *   connection.prepareStatement("SELECT * FROM users WHERE age > ? AND name
  *   LIKE ?") _ <- stmt.setInt(1, 18) _ <- stmt.setString(2, "John%") rs <-
  *   stmt.executeQuery() } yield rs
  *
  * // Batch operations val batch = for { stmt <-
  * connection.prepareStatement("INSERT INTO users (name, age) VALUES (?, ?)") _
  * <- stmt.setString(1, "Alice") _ <- stmt.setInt(2, 25) _ <- stmt.addBatch() _
  * <- stmt.setString(1, "Bob") _ <- stmt.setInt(2, 30) _ <- stmt.addBatch() }
  * yield stmt }}}
  *
  * @see
  *   [[DuckDBConnection.prepareStatement]] for creating prepared statements
  * @see
  *   [[DuckDBBatch]] for batch operations
  * @see
  *   [[DuckDBResultSet]] for query results
  * @since 0.1.0
  */
case class DuckDBPreparedStatement(
    private[duck4s] val underlying: JDBCPreparedStatement,
    val sql: String
) extends AutoCloseable:

  /** Sets an integer parameter in the prepared statement.
    *
    * @param parameterIndex
    *   the parameter index (1-based)
    * @param value
    *   the integer value to set
    * @return
    *   Right(value) on success, Left(DuckDBError) on failure
    *
    * @example
    *   {{{ val result = for { stmt <- connection.prepareStatement("SELECT *
    *   FROM users WHERE age > ?") _ <- stmt.setInt(1, 18) rs <-
    *   stmt.executeQuery() } yield rs }}}
    *
    * @since 0.1.0
    */
  def setInt(parameterIndex: Int, value: Int): Either[DuckDBError, Int] =
    try
      underlying.setInt(parameterIndex, value)
      Right(value)
    catch
      case e: SQLException =>
        Left(
          DuckDBError.QueryError(
            s"Failed to set int parameter at index $parameterIndex",
            sql,
            Some(e)
          )
        )

  /** Sets a long parameter in the prepared statement.
    *
    * @param parameterIndex
    *   the parameter index (1-based)
    * @param value
    *   the long value to set
    * @return
    *   Right(value) on success, Left(DuckDBError) on failure
    *
    * @example
    *   {{{stmt.setLong(1, 1234567890L)}}}
    *
    * @since 0.1.0
    */
  def setLong(parameterIndex: Int, value: Long): Either[DuckDBError, Long] =
    try
      underlying.setLong(parameterIndex, value)
      Right(value)
    catch
      case e: SQLException =>
        Left(
          DuckDBError.QueryError(
            s"Failed to set long parameter at index $parameterIndex",
            sql,
            Some(e)
          )
        )

  /** Sets a double parameter in the prepared statement.
    *
    * @param parameterIndex
    *   the parameter index (1-based)
    * @param value
    *   the double value to set
    * @return
    *   Right(value) on success, Left(DuckDBError) on failure
    *
    * @example
    *   {{{stmt.setDouble(1, 3.14159)}}}
    *
    * @since 0.1.0
    */
  def setDouble(
      parameterIndex: Int,
      value: Double
  ): Either[DuckDBError, Double] =
    try
      underlying.setDouble(parameterIndex, value)
      Right(value)
    catch
      case e: SQLException =>
        Left(
          DuckDBError.QueryError(
            s"Failed to set double parameter at index $parameterIndex",
            sql,
            Some(e)
          )
        )

  /** Sets a string parameter in the prepared statement.
    *
    * @param parameterIndex
    *   the parameter index (1-based)
    * @param value
    *   the string value to set
    * @return
    *   Right(value) on success, Left(DuckDBError) on failure
    *
    * @example
    *   {{{stmt.setString(1, "John Doe")}}}
    *
    * @since 0.1.0
    */
  def setString(
      parameterIndex: Int,
      value: String
  ): Either[DuckDBError, String] =
    try
      underlying.setString(parameterIndex, value)
      Right(value)
    catch
      case e: SQLException =>
        Left(
          DuckDBError.QueryError(
            s"Failed to set string parameter at index $parameterIndex",
            sql,
            Some(e)
          )
        )

  /** Sets a boolean parameter in the prepared statement.
    *
    * @param parameterIndex
    *   the parameter index (1-based)
    * @param value
    *   the boolean value to set
    * @return
    *   Right(value) on success, Left(DuckDBError) on failure
    *
    * @example
    *   {{{stmt.setBoolean(1, true)}}}
    *
    * @since 0.1.0
    */
  def setBoolean(
      parameterIndex: Int,
      value: Boolean
  ): Either[DuckDBError, Boolean] =
    try
      underlying.setBoolean(parameterIndex, value)
      Right(value)
    catch
      case e: SQLException =>
        Left(
          DuckDBError.QueryError(
            s"Failed to set boolean parameter at index $parameterIndex",
            sql,
            Some(e)
          )
        )

  /** Sets a parameter to NULL in the prepared statement.
    *
    * @param parameterIndex
    *   the parameter index (1-based)
    * @param sqlType
    *   the SQL type code (from java.sql.Types)
    * @return
    *   Right(sqlType) on success, Left(DuckDBError) on failure
    *
    * @example
    *   {{{import java.sql.Types stmt.setNull(1, Types.VARCHAR)}}}
    *
    * @since 0.1.0
    */
  def setNull(parameterIndex: Int, sqlType: Int): Either[DuckDBError, Int] =
    try
      underlying.setNull(parameterIndex, sqlType)
      Right(sqlType)
    catch
      case e: SQLException =>
        Left(
          DuckDBError.QueryError(
            s"Failed to set null parameter at index $parameterIndex",
            sql,
            Some(e)
          )
        )

  /** Executes the prepared statement as a query and returns a result set.
    *
    * This method is used for SELECT statements and other queries that return
    * data. The returned result set must be properly closed after use.
    *
    * @return
    *   Right(DuckDBResultSet) on success, Left(DuckDBError) on failure
    *
    * @example
    *   {{{ val result = for { stmt <- connection.prepareStatement("SELECT name,
    *   age FROM users WHERE id = ?") _ <- stmt.setInt(1, 123) rs <-
    *   stmt.executeQuery() } yield { if (rs.next()) { (rs.getString("name"),
    *   rs.getInt("age")) } else { ("Not found", 0) } } }}}
    *
    * @see
    *   [[DuckDBResultSet]] for processing query results
    * @since 0.1.0
    */
  def executeQuery(): Either[DuckDBError, DuckDBResultSet] =
    try
      val rs = underlying.executeQuery()
      Right(DuckDBResultSet(rs, underlying))
    catch
      case e: SQLException =>
        Left(
          DuckDBError.QueryError(
            s"Failed to execute prepared query: ${e.getMessage}",
            sql,
            Some(e)
          )
        )
      case NonFatal(e) =>
        Left(
          DuckDBError.QueryError(
            s"Unexpected error executing prepared query: ${e.getMessage}",
            sql,
            Some(e)
          )
        )

  /** Executes the prepared statement as an update and returns the number of
    * affected rows.
    *
    * This method is used for INSERT, UPDATE, DELETE statements and other SQL
    * statements that don't return a result set.
    *
    * @return
    *   Right(rowCount) on success, Left(DuckDBError) on failure
    *
    * @example
    *   {{{ val result = for { stmt <- connection.prepareStatement("UPDATE users
    *   SET age = ? WHERE name = ?") _ <- stmt.setInt(1, 26) _ <-
    *   stmt.setString(2, "Alice") rows <- stmt.executeUpdate() } yield rows
    *
    * result match { case Right(count) => println(s"Updated $count rows") case
    * Left(error) => println(s"Update failed: $error") } }}}
    *
    * @since 0.1.0
    */
  def executeUpdate(): Either[DuckDBError, Int] =
    try Right(underlying.executeUpdate())
    catch
      case e: SQLException =>
        Left(
          DuckDBError.QueryError(
            s"Failed to execute prepared update: ${e.getMessage}",
            sql,
            Some(e)
          )
        )
      case NonFatal(e) =>
        Left(
          DuckDBError.QueryError(
            s"Unexpected error executing prepared update: ${e.getMessage}",
            sql,
            Some(e)
          )
        )

  /** Adds the current parameter values to the batch for later execution.
    *
    * After setting parameters, this method adds them to the batch. Multiple
    * sets of parameters can be added to build up a batch for efficient
    * execution.
    *
    * @return
    *   Right(this) on success, Left(DuckDBError) on failure
    *
    * @example
    *   {{{ val batch = for { stmt <- connection.prepareStatement("INSERT INTO
    *   users (name, age) VALUES (?, ?)") _ <- stmt.setString(1, "Alice") _ <-
    *   stmt.setInt(2, 25) _ <- stmt.addBatch() _ <- stmt.setString(1, "Bob") _
    *   <- stmt.setInt(2, 30) _ <- stmt.addBatch() } yield stmt }}}
    *
    * @see
    *   [[DuckDBBatch]] for batch execution
    * @since 0.1.0
    */
  def addBatch(): Either[DuckDBError, DuckDBPreparedStatement] =
    try
      underlying.addBatch()
      Right(this)
    catch
      case e: SQLException =>
        Left(
          DuckDBError.QueryError(
            s"Failed to add batch: ${e.getMessage}",
            sql,
            Some(e)
          )
        )

  /** Clears all parameter values that have been set on this prepared statement.
    *
    * After calling this method, all parameters will be unset and need to be set
    * again before executing the statement.
    *
    * @return
    *   Right(this) on success, Left(DuckDBError) on failure
    *
    * @example
    *   {{{ val result = for { stmt <- connection.prepareStatement("SELECT *
    *   FROM users WHERE age > ?") _ <- stmt.setInt(1, 18) _ <-
    *   stmt.clearParameters() // Clear previous value _ <- stmt.setInt(1, 21)
    *   // Set new value rs <- stmt.executeQuery() } yield rs }}}
    *
    * @since 0.1.0
    */
  def clearParameters(): Either[DuckDBError, DuckDBPreparedStatement] =
    try
      underlying.clearParameters()
      Right(this)
    catch
      case e: SQLException =>
        Left(
          DuckDBError.QueryError(
            s"Failed to clear parameters: ${e.getMessage}",
            sql,
            Some(e)
          )
        )

  /** Closes this prepared statement and releases its database resources.
    *
    * It's important to close prepared statements when done with them to free
    * database resources. This method is called automatically when using
    * resource management patterns like `Using`.
    *
    * @example
    *   {{{ val stmt = connection.prepareStatement("SELECT * FROM users") try {
    *   // Use the statement... } finally { stmt.close() } }}}
    *
    * @since 0.1.0
    */
  def close(): Unit = underlying.close()

  /** Exports the isClosed method from the underlying PreparedStatement.
    *
    * This allows checking if the prepared statement has been closed.
    *
    * @return
    *   true if the statement is closed, false otherwise
    * @since 0.1.0
    */
  export underlying.isClosed
