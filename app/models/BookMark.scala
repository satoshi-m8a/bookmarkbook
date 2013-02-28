package models

import akka.actor._
import scala.concurrent.duration._

import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._

import akka.util.Timeout
import akka.pattern.ask

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Created with IntelliJ IDEA.
 * User: s
 * Date: 2013/02/28
 * Time: 20:44
 * To change this template use File | Settings | File Templates.
 */

object BookMark {
  implicit val timeout = Timeout(1 second)

  var bookMap = Map.empty[String, ActorRef]

  def bookmark(username: String, url: String): Unit = {
    println(username)
    println(url)
    if (bookMap.contains(username))
      bookMap(username) ! Talk(username, url)
  }

  def join(username: String): scala.concurrent.Future[(Iteratee[JsValue, _], Enumerator[JsValue])] = {
    val actor =
      if (bookMap.contains(username))
        bookMap(username)
      else
        Akka.system.actorOf(Props[BookMark])


    if (!bookMap.contains(username)) {
      bookMap = bookMap + (username -> actor)
    }
    (actor ? Join(username)).map {

      case Connected(enumerator) =>

        // Create an Iteratee to consume the feed
        val iteratee = Iteratee.foreach[JsValue] {
          event =>
            actor ! Talk(username, (event \ "text").as[String])
        }.mapDone {
          _ =>
            actor ! Quit(username)
        }

        (iteratee, enumerator)

      case CannotConnect(error) =>

        // Connection error

        // A finished Iteratee sending EOF
        val iteratee = Done[JsValue, Unit]((), Input.EOF)

        // Send an error and close the socket
        val enumerator = Enumerator[JsValue](JsObject(Seq("error" -> JsString(error)))).andThen(Enumerator.enumInput(Input.EOF))

        (iteratee, enumerator)

    }
  }
}

class BookMark extends Actor {
  var members = Set.empty[String]
  val (chatEnumerator, chatChannel) = Concurrent.broadcast[JsValue]

  def receive = {
    case Join(username) => {
      if (members.contains(username)) {
        sender ! CannotConnect("This username is already used")
      } else {
        members = members + username
        sender ! Connected(chatEnumerator)
        self ! NotifyJoin(username)
      }
    }

    case NotifyJoin(username) => {
      notifyAll("join", username, "has entered the room")
    }

    case Talk(username, text) => {
      notifyAll("talk", username, text)
    }

    case Quit(username) => {
      members = members - username
      notifyAll("quit", username, "has left the room")
    }


  }

  def notifyAll(kind: String, user: String, text: String) {
    val msg = JsObject(
      Seq(
        "kind" -> JsString(kind),
        "user" -> JsString(user),
        "message" -> JsString(text),
        "members" -> JsArray(
          members.toList.map(JsString)
        )
      )
    )
    chatChannel.push(msg)
  }
}

case class Join(username: String)

case class Quit(username: String)

case class Talk(username: String, text: String)

case class NotifyJoin(username: String)

case class Connected(enumerator: Enumerator[JsValue])

case class CannotConnect(msg: String)

