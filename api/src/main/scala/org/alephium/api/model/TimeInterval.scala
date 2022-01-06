// Copyright 2018 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see <http://www.gnu.org/licenses/>.

package org.alephium.api.model

import sttp.model.StatusCode

import org.alephium.api.ApiError
import org.alephium.util.{Duration, TimeStamp}

final case class TimeInterval(from: TimeStamp, to: TimeStamp) {
  def validateTimeSpan(max: Duration): Either[ApiError[_ <: StatusCode], Unit] = {
    if (durationUnsafe() > max) {
      Left(ApiError.BadRequest(s"Time span cannot be greater than ${max}"))
    } else {
      Right(())
    }
  }

  def durationUnsafe(): Duration = to.deltaUnsafe(from)
}
