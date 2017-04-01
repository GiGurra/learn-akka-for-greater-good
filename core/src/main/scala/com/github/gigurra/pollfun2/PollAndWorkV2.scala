package com.github.gigurra.pollfun2

import java.util.concurrent.TimeUnit

import akka.actor.SupervisorStrategy.Escalate
import akka.actor.{Actor, ActorRef, ActorSystem, FSM, OneForOneStrategy, Props, SupervisorStrategy}
import com.github.gigurra.pollfun2
import com.github.gigurra.pollfun2.MasterActor._

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

object PollAndWorkV2 extends App {
  ActorSystem().actorOf(Props[MasterActor])
}

class MasterActor extends FSM[State, WorkLeft] {

  println("STARTING A NEW MASTER ACTOR")

  // If a state crashes along the way, escalate so we can be restarted
  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(maxNrOfRetries = 0){ case NonFatal(_) => Escalate }

  startWith(Idle, WorkLeft.empty)

  when(Idle, stateTimeout = FiniteDuration(3, TimeUnit.SECONDS)) {
    case Event(StateTimeout, _) =>
      println(s"Lets go!")
      println(s"spawning persistence actor")
      spawnChild(Props[PersistenceActor])
      goto(WaitForDbData) using WorkLeft.empty
  }

  when(WaitForDbData) {
    case Event(PersistenceReturn(items, success), state) =>
      if (success) {
        println(s"Persistence actor succeeded")
        spawnWorkers(items)
        goto(WaitForWorkers) using state.copy(itemsLeft = mutable.HashSet(items: _*))
      } else {
        println(s"Persistence actor failed, resetting")
        goto(Idle)
      }
  }

  when(WaitForWorkers) {
    case Event(WorkItemFinished(data, success), state) => handleWorkItemFinished(data, state, success = success)
  }

  initialize()

  ////////////////////////////////////////////////////////////////

  private def spawnWorkers(data: Seq[String]): mutable.HashSet[ActorRef] = {
    mutable.HashSet[ActorRef]() ++ data.map(item => spawnChild(Props(new WorkerActor(item))))
  }

  private def spawnChild(props: Props): ActorRef = {
    context.watch(context.actorOf(props))
  }

  private def handleWorkItemFinished(item: String, state: WorkLeft, success: Boolean): State = {
    println(s"Work ${if (success) "finished successfully" else "failed"} on item '$item'")
    state.itemsLeft -= item
    if (state.itemsLeft.isEmpty) {
      println(s"All work items finished, resetting Master Actor")
      for (_ <- 0 until 10) println()
      goto(Idle)
    } else {
      stay()
    }
  }
}

object MasterActor {
  sealed trait State
  case object Idle extends State
  case object WaitForDbData extends State
  case object WaitForWorkers extends State

  case class WorkLeft(itemsLeft: mutable.HashSet[String] = new mutable.HashSet)
  object WorkLeft { def empty: WorkLeft = WorkLeft() }
}


class PersistenceActor extends Actor {
  self ! LetsGo
  override def receive: Receive = {
    case LetsGo =>
      println(s"$this fetching data from db")
      if (math.random < 0.9) {
        println(s"$this: OK!")
        context.parent ! pollfun2.PersistenceReturn(Seq("a", "b", "c"), success = true)
      } else {
        println(s"$this: FAIL!")
        context.parent ! pollfun2.PersistenceReturn(Seq("a", "b", "c"), success = false)
      }
  }
}

class WorkerActor(data: String) extends Actor {
  self ! LetsGo
  override def receive: Receive = {
    case LetsGo =>
      println(s"$this doing work on: '$data'")
      if (math.random < 0.9) {
        println(s"$this: OK!")
        context.parent ! WorkItemFinished(data, success = true)
      } else {
        println(s"$this: FAIL!")
        context.parent ! WorkItemFinished(data, success = false)
      }
  }
}

case object LetsGo
case class PersistenceReturn(data: Seq[String], success: Boolean)
case class WorkItemFinished(data: String, success: Boolean)
