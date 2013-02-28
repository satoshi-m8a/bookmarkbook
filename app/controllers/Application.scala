package controllers

import play.api.mvc._
import play.api.libs.json.JsValue
import models.BookMark


object Application extends Controller {
  def index = Action {
    implicit request =>
      Ok(views.html.index())
  }

  def bookRoom(username: Option[String]) = Action {
    implicit request =>
      username.filterNot(_.isEmpty).map {
        username =>
          Ok(views.html.bookRoom(username))
      }.getOrElse {
        Redirect(routes.Application.index).flashing(
          "error" -> "Please choose a valid username."
        )
      }
  }

  def stream(username: String) = WebSocket.async[JsValue] {
    request =>
      BookMark.join(username)
  }

  def bookmark(username: String, url: String) = Action {
    implicit request =>
      BookMark.bookmark(username, url)
      Ok(views.html.index()).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
  }
}