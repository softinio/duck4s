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

/** Root error type for all DuckDB-related errors in duck4s.
  *
  * This sealed trait represents all possible errors that can occur when working
  * with DuckDB through the duck4s library. All operations return
  * `Either[DuckDBError, T]` for functional error handling.
  *
  * @see
  *   [[DuckDBError.ConnectionError]] for connection-related errors
  * @see
  *   [[DuckDBError.QueryError]] for SQL execution errors
  * @see
  *   [[DuckDBError.TransactionError]] for transaction-related errors
  * @see
  *   [[DuckDBError.ConfigurationError]] for configuration errors
  * @see
  *   [[DuckDBError.InvalidStateError]] for invalid state errors
  * @since 0.1.0
  */
sealed trait DuckDBError

/** Contains all concrete error types that can occur in duck4s operations. */
object DuckDBError:

  /** Represents errors that occur during connection establishment or
    * management.
    *
    * @param message
    *   Human-readable error description
    * @param cause
    *   Optional underlying throwable that caused this error
    * @since 0.1.0
    */
  case class ConnectionError(message: String, cause: Option[Throwable] = None)
      extends DuckDBError

  /** Represents errors that occur during SQL query or statement execution.
    *
    * @param message
    *   Human-readable error description
    * @param sql
    *   The SQL statement that caused the error
    * @param cause
    *   Optional underlying throwable that caused this error
    * @since 0.1.0
    */
  case class QueryError(
      message: String,
      sql: String,
      cause: Option[Throwable] = None
  ) extends DuckDBError

  /** Represents errors that occur during transaction operations.
    *
    * @param message
    *   Human-readable error description
    * @param cause
    *   Optional underlying throwable that caused this error
    * @since 0.1.0
    */
  case class TransactionError(message: String, cause: Option[Throwable] = None)
      extends DuckDBError

  /** Represents errors related to invalid configuration parameters.
    *
    * @param message
    *   Human-readable error description
    * @since 0.1.0
    */
  case class ConfigurationError(message: String) extends DuckDBError

  /** Represents errors that occur when operations are attempted on invalid
    * object states.
    *
    * @param message
    *   Human-readable error description
    * @since 0.1.0
    */
  case class InvalidStateError(message: String) extends DuckDBError
