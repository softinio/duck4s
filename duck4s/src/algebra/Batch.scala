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

import scala.util.control.NonFatal
import java.sql.SQLException

/** A batch operation wrapper for executing multiple parameter sets efficiently.
  *
  * This class provides a type-safe way to execute batch operations with
  * prepared statements. It uses type classes ([[BatchBinder]] and
  * [[ParameterBinder]]) to automatically bind parameters of various types,
  * making batch operations both safe and convenient.
  *
  * @param preparedStatement
  *   the prepared statement to use for batch operations
  *
  * @example
  *   {{{ // Batch insert with tuples val batch = for { stmt <-
  *   connection.prepareStatement("INSERT INTO users (name, age) VALUES (?, ?)")
  *   batch <- Right(DuckDBBatch(stmt)) _ <- batch.addBatch(("Alice", 25),
  *   ("Bob", 30), ("Charlie", 35)) result <- batch.executeBatch() } yield
  *   result
  *
  * // Batch update with different parameter types val updateBatch = for { stmt
  * <- connection.prepareStatement("UPDATE products SET price = ?, active = ?
  * WHERE id = ?") batch <- Right(DuckDBBatch(stmt)) _ <- batch.addBatch((19.99,
  * true, 1), (29.99, false, 2)) result <- batch.executeBatch() } yield result
  * }}}
  *
  * @see
  *   [[BatchBinder]] for parameter binding type class
  * @see
  *   [[ParameterBinder]] for individual parameter binding
  * @see
  *   [[DuckDBBatchResult]] for batch execution results
  * @since 0.1.0
  */
case class DuckDBBatch(
    private val preparedStatement: DuckDBPreparedStatement
) extends AutoCloseable:

  /** Adds multiple parameter sets to the batch for execution.
    *
    * This method uses the [[BatchBinder]] type class to automatically bind
    * parameters of the specified type. The type class provides compile-time
    * safety for parameter binding.
    *
    * @tparam T
    *   the type of parameter values (typically tuples)
    * @param values
    *   the parameter values to add to the batch
    * @param binder
    *   the implicit [[BatchBinder]] for type T
    * @return
    *   Right(this) on success, Left(DuckDBError) on failure
    *
    * @example
    *   {{{ // Adding tuples to batch batch.addBatch( ("Alice", 25), ("Bob",
    *   30), ("Charlie", 35) )
    *
    * // Adding single values with Option support batch.addBatch( ("Product A",
    * Some(19.99)), ("Product B", None) ) }}}
    *
    * @since 0.1.0
    */
  def addBatch[T](
      values: T*
  )(using binder: BatchBinder[T]): Either[DuckDBError, DuckDBBatch] =
    binder
      .bind(preparedStatement, values*)
      .flatMap(_ => preparedStatement.addBatch())
      .map(_ => this)

  /** Executes all batched parameter sets and returns the results.
    *
    * This method executes all parameter sets that have been added to the batch
    * and returns a [[DuckDBBatchResult]] containing update counts and
    * statistics.
    *
    * @return
    *   Right(DuckDBBatchResult) on success, Left(DuckDBError) on failure
    *
    * @example
    *   {{{ val result = for { batch <- Right(DuckDBBatch(stmt)) _ <-
    *   batch.addBatch(("Alice", 25), ("Bob", 30)) result <-
    *   batch.executeBatch() } yield result
    *
    * result match { case Right(batchResult) => println(s"Successful operations:
    * ${batchResult.successCount}") println(s"Failed operations:
    * ${batchResult.failureCount}") case Left(error) => println(s"Batch
    * execution failed: $error") } }}}
    *
    * @see
    *   [[DuckDBBatchResult]] for result details
    * @since 0.1.0
    */
  def executeBatch(): Either[DuckDBError, DuckDBBatchResult] =
    try
      val updateCounts = preparedStatement.underlying.executeBatch()
      val successCount = updateCounts.count(_ >= 0)
      val failureCount = updateCounts.count(_ < 0)
      Right(DuckDBBatchResult(updateCounts, successCount, failureCount))
    catch
      case e: SQLException =>
        Left(
          DuckDBError.QueryError(
            s"Failed to execute batch: ${e.getMessage}",
            preparedStatement.sql,
            Some(e)
          )
        )
      case NonFatal(e) =>
        Left(
          DuckDBError.QueryError(
            s"Unexpected error executing batch: ${e.getMessage}",
            preparedStatement.sql,
            Some(e)
          )
        )

  /** Clears all parameter sets from the batch without executing them.
    *
    * This method removes all previously added parameter sets from the batch,
    * allowing you to start over with new parameter sets.
    *
    * @return
    *   Right(this) on success, Left(DuckDBError) on failure
    *
    * @example
    *   {{{ val result = for { batch <- Right(DuckDBBatch(stmt)) _ <-
    *   batch.addBatch(("Alice", 25)) _ <- batch.clearBatch() // Remove Alice _
    *   <- batch.addBatch(("Bob", 30)) // Add Bob instead result <-
    *   batch.executeBatch() } yield result }}}
    *
    * @since 0.1.0
    */
  def clearBatch(): Either[DuckDBError, DuckDBBatch] =
    try
      preparedStatement.underlying.clearBatch()
      Right(this)
    catch
      case e: SQLException =>
        Left(
          DuckDBError.QueryError(
            s"Failed to clear batch: ${e.getMessage}",
            preparedStatement.sql,
            Some(e)
          )
        )

  /** Closes the underlying prepared statement and releases database resources.
    *
    * This method closes the prepared statement used by this batch. It should be
    * called when the batch is no longer needed to free database resources.
    *
    * @example
    *   {{{ val batch = DuckDBBatch(stmt) try { // Use the batch... } finally {
    *   batch.close() } }}}
    *
    * @since 0.1.0
    */
  def close(): Unit = preparedStatement.close()

/** Type class for binding batch parameters to prepared statements.
  *
  * This type class provides a way to bind different types of parameter values
  * to prepared statements in a type-safe manner. Implementations are provided
  * for tuples of various sizes, allowing batch operations with multiple
  * parameters.
  *
  * @tparam T
  *   the type of parameter values to bind
  *
  * @example
  *   {{{ // Custom binder for a case class case class User(name: String, age:
  *   Int)
  *
  * given BatchBinder[User] with def bind(stmt: DuckDBPreparedStatement, values:
  * User*): Either[DuckDBError, Unit] = if (values.isEmpty) Right(()) else val
  * user = values.head for { _ <- stmt.setString(1, user.name) _ <-
  * stmt.setInt(2, user.age) } yield () }}}
  *
  * @see
  *   [[ParameterBinder]] for individual parameter binding
  * @since 0.1.0
  */
trait BatchBinder[T]:
  /** Binds parameter values to a prepared statement.
    *
    * @param stmt
    *   the prepared statement to bind parameters to
    * @param values
    *   the parameter values to bind
    * @return
    *   Right(()) on success, Left(DuckDBError) on failure
    */
  def bind(stmt: DuckDBPreparedStatement, values: T*): Either[DuckDBError, Unit]

/** Companion object providing implicit [[BatchBinder]] instances for common
  * types.
  *
  * This object contains given instances for tuples of various sizes (2, 3, and
  * 4 elements). Each binder uses the appropriate [[ParameterBinder]] instances
  * for the tuple elements.
  *
  * @since 0.1.0
  */
object BatchBinder:
  /** Implicit [[BatchBinder]] for 2-element tuples.
    *
    * @tparam A
    *   the type of the first element
    * @tparam B
    *   the type of the second element
    * @param ba
    *   implicit [[ParameterBinder]] for type A
    * @param bb
    *   implicit [[ParameterBinder]] for type B
    * @return
    *   a [[BatchBinder]] for (A, B) tuples
    *
    * @example
    *   {{{batch.addBatch( ("Alice", 25), ("Bob", 30) )}}}
    *
    * @since 0.1.0
    */
  given batchBinder2[A, B](using
      ba: ParameterBinder[A],
      bb: ParameterBinder[B]
  ): BatchBinder[(A, B)] with
    def bind(
        stmt: DuckDBPreparedStatement,
        values: (A, B)*
    ): Either[DuckDBError, Unit] =
      if values.isEmpty then Right(())
      else
        val (a, b) = values.head
        for
          _ <- ba.bind(stmt, 1, a)
          _ <- bb.bind(stmt, 2, b)
        yield ()

  /** Implicit [[BatchBinder]] for 3-element tuples.
    *
    * @tparam A
    *   the type of the first element
    * @tparam B
    *   the type of the second element
    * @tparam C
    *   the type of the third element
    * @param ba
    *   implicit [[ParameterBinder]] for type A
    * @param bb
    *   implicit [[ParameterBinder]] for type B
    * @param bc
    *   implicit [[ParameterBinder]] for type C
    * @return
    *   a [[BatchBinder]] for (A, B, C) tuples
    *
    * @example
    *   {{{ batch.addBatch( ("Product A", 19.99, true), ("Product B", 29.99,
    *   false) ) }}}
    *
    * @since 0.1.0
    */
  given batchBinder3[A, B, C](using
      ba: ParameterBinder[A],
      bb: ParameterBinder[B],
      bc: ParameterBinder[C]
  ): BatchBinder[(A, B, C)] with
    def bind(
        stmt: DuckDBPreparedStatement,
        values: (A, B, C)*
    ): Either[DuckDBError, Unit] =
      if values.isEmpty then Right(())
      else
        val (a, b, c) = values.head
        for
          _ <- ba.bind(stmt, 1, a)
          _ <- bb.bind(stmt, 2, b)
          _ <- bc.bind(stmt, 3, c)
        yield ()

  /** Implicit [[BatchBinder]] for 4-element tuples.
    *
    * @tparam A
    *   the type of the first element
    * @tparam B
    *   the type of the second element
    * @tparam C
    *   the type of the third element
    * @tparam D
    *   the type of the fourth element
    * @param ba
    *   implicit [[ParameterBinder]] for type A
    * @param bb
    *   implicit [[ParameterBinder]] for type B
    * @param bc
    *   implicit [[ParameterBinder]] for type C
    * @param bd
    *   implicit [[ParameterBinder]] for type D
    * @return
    *   a [[BatchBinder]] for (A, B, C, D) tuples
    *
    * @example
    *   {{{ batch.addBatch( ("Alice", 25, "Engineer", 75000.0), ("Bob", 30,
    *   "Manager", 85000.0) ) }}}
    *
    * @since 0.1.0
    */
  given batchBinder4[A, B, C, D](using
      ba: ParameterBinder[A],
      bb: ParameterBinder[B],
      bc: ParameterBinder[C],
      bd: ParameterBinder[D]
  ): BatchBinder[(A, B, C, D)] with
    def bind(
        stmt: DuckDBPreparedStatement,
        values: (A, B, C, D)*
    ): Either[DuckDBError, Unit] =
      if values.isEmpty then Right(())
      else
        val (a, b, c, d) = values.head
        for
          _ <- ba.bind(stmt, 1, a)
          _ <- bb.bind(stmt, 2, b)
          _ <- bc.bind(stmt, 3, c)
          _ <- bd.bind(stmt, 4, d)
        yield ()

/** Type class for binding individual parameters to prepared statements.
  *
  * This type class provides a way to bind individual parameter values to
  * prepared statements at specific parameter indexes. Implementations are
  * provided for common Scala types and Option types.
  *
  * @tparam T
  *   the type of parameter value to bind
  *
  * @example
  *   {{{ // Custom binder for a custom type case class UserId(value: Long)
  *
  * given ParameterBinder[UserId] with def bind( stmt: DuckDBPreparedStatement,
  * index: Int, value: UserId ): Either[DuckDBError, Unit] = stmt.setLong(index,
  * value.value).map(_ => ()) }}}
  *
  * @see
  *   [[BatchBinder]] for batch parameter binding
  * @since 0.1.0
  */
trait ParameterBinder[T]:
  /** Binds a parameter value to a prepared statement at the specified index.
    *
    * @param stmt
    *   the prepared statement to bind the parameter to
    * @param index
    *   the parameter index (1-based)
    * @param value
    *   the parameter value to bind
    * @return
    *   Right(()) on success, Left(DuckDBError) on failure
    */
  def bind(
      stmt: DuckDBPreparedStatement,
      index: Int,
      value: T
  ): Either[DuckDBError, Unit]

/** Companion object providing implicit [[ParameterBinder]] instances for common
  * types.
  *
  * This object contains given instances for primitive types (Int, Long, Double,
  * String, Boolean) and Option types, providing automatic parameter binding for
  * these common cases.
  *
  * @since 0.1.0
  */
object ParameterBinder:
  /** Implicit [[ParameterBinder]] for Int values.
    *
    * @example
    *   {{{ // Used automatically in batch operations batch.addBatch(("Alice",
    *   25)) // 25 bound as Int }}}
    *
    * @since 0.1.0
    */
  given intBinder: ParameterBinder[Int] with
    def bind(
        stmt: DuckDBPreparedStatement,
        index: Int,
        value: Int
    ): Either[DuckDBError, Unit] =
      stmt.setInt(index, value).map(_ => ())

  /** Implicit [[ParameterBinder]] for Long values.
    *
    * @example
    *   {{{batch.addBatch((1234567890L, "data"))}}}
    *
    * @since 0.1.0
    */
  given longBinder: ParameterBinder[Long] with
    def bind(
        stmt: DuckDBPreparedStatement,
        index: Int,
        value: Long
    ): Either[DuckDBError, Unit] =
      stmt.setLong(index, value).map(_ => ())

  /** Implicit [[ParameterBinder]] for Double values.
    *
    * @example
    *   {{{batch.addBatch(("Product", 19.99))}}}
    *
    * @since 0.1.0
    */
  given doubleBinder: ParameterBinder[Double] with
    def bind(
        stmt: DuckDBPreparedStatement,
        index: Int,
        value: Double
    ): Either[DuckDBError, Unit] =
      stmt.setDouble(index, value).map(_ => ())

  /** Implicit [[ParameterBinder]] for String values.
    *
    * @example
    *   {{{batch.addBatch(("Alice", "Engineer"))}}}
    *
    * @since 0.1.0
    */
  given stringBinder: ParameterBinder[String] with
    def bind(
        stmt: DuckDBPreparedStatement,
        index: Int,
        value: String
    ): Either[DuckDBError, Unit] =
      stmt.setString(index, value).map(_ => ())

  /** Implicit [[ParameterBinder]] for Boolean values.
    *
    * @example
    *   {{{batch.addBatch(("Product A", true)) // true for active}}}
    *
    * @since 0.1.0
    */
  given booleanBinder: ParameterBinder[Boolean] with
    def bind(
        stmt: DuckDBPreparedStatement,
        index: Int,
        value: Boolean
    ): Either[DuckDBError, Unit] =
      stmt.setBoolean(index, value).map(_ => ())

  /** Implicit [[ParameterBinder]] for Option values.
    *
    * This binder handles nullable parameters by binding the wrapped value if
    * present, or NULL if the Option is None.
    *
    * @tparam T
    *   the type of the wrapped value
    * @param binder
    *   implicit [[ParameterBinder]] for the wrapped type T
    * @return
    *   a [[ParameterBinder]] for Option[T]
    *
    * @example
    *   {{{ // Some values are bound normally, None becomes NULL batch.addBatch(
    *   ("Alice", Some(25)), // age = 25 ("Bob", None) // age = NULL ) }}}
    *
    * @since 0.1.0
    */
  given optionBinder[T](using binder: ParameterBinder[T]): ParameterBinder[
    Option[T]
  ] with
    def bind(
        stmt: DuckDBPreparedStatement,
        index: Int,
        value: Option[T]
    ): Either[DuckDBError, Unit] =
      value match
        case Some(v) => binder.bind(stmt, index, v)
        case None    => stmt.setNull(index, java.sql.Types.NULL).map(_ => ())
