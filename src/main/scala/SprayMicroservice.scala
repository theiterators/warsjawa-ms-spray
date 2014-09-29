import reactivemongo.api.MongoDriver
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.bson.{BSONDocument, Macros, BSONObjectID}
import redis.RedisClient
import akka.actor.ActorSystem
import akka.event.Logging._
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.json._
import StatusCodes._
import spray.routing.SimpleRoutingApp
import scala.concurrent.Future
import spray.httpx.marshalling._
import scala.concurrent.ExecutionContext.Implicits.global

case class AccountPostRequest(login: String, password: String, passwordConfirmation: String) {
  require(password == passwordConfirmation, "Password and password confirmation don't match")
  require(login.length > 0, "Login must be at least one character")
  require(password.length > 5, "Password must be at least 5 characters")
}

case class AccountConfirmation(login: String, authToken: String)

case class Account(_id: Option[BSONObjectID] = Some(BSONObjectID.generate), login: String, password: String, date: Long)

case class AuthenticationResposne(login: String, authToken: String)

object Converters extends DefaultJsonProtocol {
  implicit val bsonObjectIdFormat = new JsonFormat[BSONObjectID] {
    override def write(obj: BSONObjectID): JsValue = JsString(obj.stringify)

    override def read(json: JsValue): BSONObjectID = BSONObjectID(json.toString())
  }
  implicit val accountPostRequestJson = jsonFormat3(AccountPostRequest)
  implicit val accountConfirmationJson = jsonFormat2(AccountConfirmation)
  implicit val accountJson = jsonFormat4(Account)
  implicit val accountBson = Macros.handler[Account]
  implicit val authenticationResponseJson = jsonFormat2(AuthenticationResposne)
}

object SprayMicroservice extends App with SimpleRoutingApp {
  import Converters._

  implicit val _ = ActorSystem()
  val driver = new MongoDriver
  val connection = driver.connection(List("localhost"))
  val db = connection("warsjawa")
  val accounts: BSONCollection = db("accounts")
  val redis = RedisClient()

  startServer(interface = "0.0.0.0", port = 8001) {
    logRequestResponse("spray-microservice", InfoLevel) {
      pathPrefix("auth") {
        path("account") {
          post {
            entity(as[AccountPostRequest]) { accountPostRequest =>
              complete {
                val account = Account(login = accountPostRequest.login, password = accountPostRequest.password, date = System.currentTimeMillis())
                val authToken = java.util.UUID.randomUUID().toString

                val accountExistsFuture = accounts.find(BSONDocument("login" -> account.login)).one[Account].map(_.isDefined)

                accountExistsFuture.flatMap { accountExists =>
                  if (accountExists) {
                    Future.successful(HttpResponse(BadRequest, "User with given login exists"))
                  } else {
                    val accountInsertFuture = accounts.insert(account)
                    val authTokenInsertFuture = redis.set(s"auth:token:$authToken", account.login)
                    for {
                      accountInsert <- accountInsertFuture if accountInsert.ok
                      authTokenInsert <- authTokenInsertFuture if authTokenInsert
                    } yield {
                      val response = AuthenticationResposne(account.login, authToken)
                      HttpResponse(OK, marshalUnsafe(response))
                    }
                  }
                }
              }
            }
          } ~
          get {
            headerValueByName("Auth-Token") { authToken =>
              complete {
                val loginFuture = redis.get(s"auth:token:$authToken")

                loginFuture.flatMap { loginOption =>
                  val login = loginOption.map(_.utf8String).getOrElse(throw new IllegalArgumentException("No user for specified auth token found"))
                  accounts.find(BSONDocument("login" -> login)).one[Account].map { accountOption =>
                    HttpResponse(OK, marshalUnsafe(accountOption))
                  }
                }.fallbackTo(Future.successful(HttpResponse(Forbidden)))
              }
            }
          }
        } ~
        path("session") {
          formFields('login, 'password) { (login, password) =>
            complete {
              accounts.find(BSONDocument("login" -> login, "password" -> password)).one[Account].map {
                case Some(account) =>
                  val authToken = java.util.UUID.randomUUID().toString
                  HttpResponse(OK, marshalUnsafe(AuthenticationResposne(account.login, authToken)))
                case None => HttpResponse(Forbidden)
              }
            }
          }
        }
      }
    }
  }
}
