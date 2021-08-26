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

package org.alephium.flow.mining

import java.net.InetSocketAddress

import scala.concurrent.ExecutionContext

import akka.actor.ActorRef
import akka.testkit.{TestActor, TestProbe}
import org.scalatest.concurrent.Eventually

import org.alephium.flow.handler.{TestUtils, ViewHandler}
import org.alephium.flow.mining.{ExternalMinerMock, Miner, MinerApiController}
import org.alephium.flow.setting.AlephiumConfigFixture
import org.alephium.util.{AlephiumActorSpec, AVector, Duration, SocketUtil}

class ExternalMinerMockSpec extends AlephiumActorSpec with Eventually {
  override def actorSystemConfig = AlephiumActorSpec.infoConfig

  it should "reconnect to multiple nodes" in new ExternalMinerMockFixture {
    minerApiControllers.foreach(_.underlyingActor.connections.size is 0)
    miner ! Miner.Start
    minerApiControllers.foreach(c => eventually(c.underlyingActor.connections.size is 1))
    minerApiControllers.foreach(c => system.stop(c.underlyingActor.connections(0).ref))
    minerApiControllers.foreach(c => eventually(c.underlyingActor.connections.size is 0))
    minerApiControllers.foreach(c => eventually(c.underlyingActor.connections.size is 1))
    minerApiControllers.foreach(system.stop)
    system.stop(miner)
  }

  it should "shutdown if a minerApiController is stopped" in new ExternalMinerMockFixture {
    minerProbe watch miner
    minerApiControllers.foreach(c => c.underlyingActor.connections.size is 0)
    miner ! Miner.Start
    minerApiControllers.foreach(c => eventually(c.underlyingActor.connections.size is 1))
    system.stop(minerApiControllers.head)
    // We wait 1 min because Windows takes more time due to a slower I/O operation with java.nio.SocketChannel
    minerProbe.expectTerminated(miner, Duration.ofMinutesUnsafe(1).asScala)
    minerApiControllers.tail.foreach(system.stop)
  }

  it should "shutdown if a minerApiController connection is stopped" in new ExternalMinerMockFixture {
    minerProbe watch miner
    handlersProbes.head.viewHandler.setAutoPilot((sender: ActorRef, msg: Any) =>
      msg match {
        case ViewHandler.Subscribe =>
          sender ! ViewHandler.SubscribeResult(false)
          TestActor.KeepRunning
      }
    )
    minerApiControllers.foreach(_.underlyingActor.connections.size is 0)
    miner ! Miner.Start
    minerApiControllers.tail.foreach(c => eventually(c.underlyingActor.connections.size is 1))
    minerProbe.expectTerminated(miner, Duration.ofSecondsUnsafe(10.toLong).asScala)
    minerApiControllers.tail.foreach(system.stop)
  }

  trait ExternalMinerMockFixture extends SocketUtil with AlephiumConfigFixture {
    implicit val executionContext: ExecutionContext = system.dispatcher
    override val configValues: Map[String, Any] = Map(
      "alephium.network.backoff-base-delay" -> "5 milli"
    )

    lazy val controllersAddresses =
      (1 to groups0).map(_ => new InetSocketAddress(config.mining.apiInterface, generatePort()))

    lazy val (handlers, handlersProbes) =
      controllersAddresses.map(_ => TestUtils.createAllHandlersProbe).unzip

    handlersProbes.foreach(
      _.viewHandler.setAutoPilot((sender: ActorRef, msg: Any) =>
        msg match {
          case ViewHandler.Subscribe =>
            sender ! ViewHandler.SubscribeResult(true)
            TestActor.KeepRunning
          case ViewHandler.Unsubscribe =>
            TestActor.KeepRunning
        }
      )
    )

    lazy val minerApiControllers =
      controllersAddresses.zip(handlers).map { case (address, allHandlers) =>
        newTestActorRef[MinerApiController](
          MinerApiController.props(allHandlers)(
            config.broker,
            config.network.copy(minerApiPort = address.getPort()),
            config.mining
          )
        )
      }

    lazy val minerProbe = TestProbe()
    lazy val miner = newTestActorRef[Miner](
      ExternalMinerMock.props(AVector.from(controllersAddresses))
    )
  }
}
