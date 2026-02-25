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

import com.softinio.duck4s.algebra.DuckDBError

/** A [[Throwable]] wrapper for [[com.softinio.duck4s.algebra.DuckDBError]],
  * enabling error values to be raised into cats-effect IO.
  *
  * @param error
  *   The underlying DuckDBError
  * @param message
  *   Human-readable error message
  * @param cause
  *   Optional underlying throwable that caused this error
  */
class DuckDBException(
    val error: DuckDBError,
    message: String,
    cause: Option[Throwable] = None
) extends RuntimeException(message, cause.orNull)

/** Factory methods for creating [[DuckDBException]] from [[DuckDBError]]
  * values.
  */
object DuckDBException:

  /** Converts a [[DuckDBError]] into a [[DuckDBException]].
    *
    * @param error
    *   The DuckDBError to wrap
    * @return
    *   A DuckDBException wrapping the given error
    */
  def from(error: DuckDBError): DuckDBException = error match
    case DuckDBError.ConnectionError(msg, cause)  => DuckDBException(error, msg, cause)
    case DuckDBError.QueryError(msg, _, cause)     => DuckDBException(error, msg, cause)
    case DuckDBError.TransactionError(msg, cause)  => DuckDBException(error, msg, cause)
    case DuckDBError.ConfigurationError(msg)       => DuckDBException(error, msg)
    case DuckDBError.InvalidStateError(msg)        => DuckDBException(error, msg)
